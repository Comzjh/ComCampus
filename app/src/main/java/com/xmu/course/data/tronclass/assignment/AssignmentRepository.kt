package com.xmu.course.data.tronclass.assignment

import androidx.room.withTransaction
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseParser
import com.xmu.course.data.tronclass.api.TronSemesterParser
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.todo.TodoDao
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AssignmentRepositoryContract {
    suspend fun syncAssignments(): TronResult<Int>
}

/** 将 TronClass 作业逐课程读取、解析并幂等写入现有 Todo 表。 */
class AssignmentRepository(
    private val api: TronClassApiService,
    private val sessionProvider: SessionProvider,
    private val todoDao: TodoDao,
    private val database: AppDatabase,
    private val parser: AssignmentParser = AssignmentParser(),
    private val semesterParser: TronSemesterParser = TronSemesterParser(),
    private val courseParser: TronCourseParser = TronCourseParser(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : AssignmentRepositoryContract {

    private val syncMutex = Mutex()

    override suspend fun syncAssignments(): TronResult<Int> = syncMutex.withLock {
        // Keep the semester/course snapshot and its commit in the same refresh order.
        withContext(Dispatchers.IO) {
            syncAssignmentsOnIo()
        }
    }

    private suspend fun syncAssignmentsOnIo(): TronResult<Int> {
        val session = runCatching { sessionProvider.getSession() }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
            ?: return TronResult.Error(TronClassError.SessionMissing)
        if (session.isExpired(clock())) {
            return TronResult.Error(TronClassError.SessionMissing)
        }

        val semestersResponse = try {
            api.getMySemesters()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return TronResult.Error(TronClassError.NetworkError)
        } catch (_: Exception) {
            return TronResult.Error(TronClassError.ResponseFormatChanged)
        }
        if (semestersResponse.code() == 401 || semestersResponse.code() == 403) {
            return TronResult.Error(TronClassError.Unauthorized)
        }
        if (!semestersResponse.isSuccessful) {
            return TronResult.Error(TronClassError.ServerError(semestersResponse.code()))
        }
        val semestersBody = semestersResponse.body()
            ?: return TronResult.Error(TronClassError.ResponseFormatChanged)
        val activeSemesterId = when (val result = semesterParser.resolve(semestersBody)) {
            is TronResult.Error -> return result
            is TronResult.Success -> result.value
        }

        val coursesResponse = try {
            api.getMyCourses()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return TronResult.Error(TronClassError.NetworkError)
        } catch (_: Exception) {
            return TronResult.Error(TronClassError.ResponseFormatChanged)
        }
        if (coursesResponse.code() == 401 || coursesResponse.code() == 403) {
            return TronResult.Error(TronClassError.Unauthorized)
        }
        if (!coursesResponse.isSuccessful) {
            return TronResult.Error(TronClassError.ServerError(coursesResponse.code()))
        }
        val coursesBody = coursesResponse.body()
            ?: return TronResult.Error(TronClassError.ResponseFormatChanged)
        when (val parsedCourses = courseParser.parse(coursesBody)) {
            is TronResult.Error -> return parsedCourses
            is TronResult.Success -> Unit
        }
        val activeCourseIds = courseParser.activeCourseIds(coursesBody, activeSemesterId)
        val knownInactiveCourseIds = courseParser.knownInactiveCourseIds(coursesBody, activeSemesterId)
        val assignments = ArrayList<TronAssignmentWithCourse>()
        // 课程数量可能达到数十门；只对当前 active 学期课程读取作业，并采用小批量并发。
        for (courseBatch in activeCourseIds.sorted().chunked(MAX_CONCURRENT_COURSE_REQUESTS)) {
            val batchResults = coroutineScope {
                courseBatch.map { courseId ->
                    async {
                        courseId to fetchAllPages(courseId)
                    }
                }.awaitAll()
            }
            batchResults.forEach { (courseId, pageResult) ->
                when (pageResult) {
                    is TronResult.Error -> return pageResult
                    is TronResult.Success -> pageResult.value.forEach { assignment ->
                        // 作业接口按课程请求，优先使用响应的稳定远端课程 ID；缺失时回退到请求路径的 ID。
                        val matchedCourseId = assignment.remoteCourseId ?: courseId
                        assignments += TronAssignmentWithCourse(assignment, matchedCourseId)
                    }
                }
            }
        }

        // 只有网络、分页和完整解析都成功后才捕获候选 baseline；失败路径不会建立它。
        val candidateBaselineAt = clock()
        return runCatching {
            database.withTransaction {
                val metadataDao = database.tronTodoSyncMetadataDao()
                val existingMetadata = metadataDao.get()
                val baselineWasCreated = if (existingMetadata == null) {
                    metadataDao.insertIfAbsent(
                        TronTodoSyncMetadataEntity(importBaselineAt = candidateBaselineAt),
                    ) != -1L
                } else {
                    false
                }
                val baseline = metadataDao.get()?.importBaselineAt
                    ?: error("TronClass import baseline was not persisted")
                // Read inside this transaction so updates and first-import filtering agree.
                val existingExternalIds = todoDao.getAll().asSequence()
                    .filter { it.source == TodoSource.TRONCLASS.name }
                    .mapNotNull { it.externalId }
                    .toSet()

                // IDs must come from the complete snapshot, before the first-import filter.
                val assignmentIdsInSnapshot = assignments
                    .mapTo(mutableSetOf()) { it.assignment.externalId }

                val eligibleAssignments = assignments
                    .filter { item ->
                        item.assignment.externalId in existingExternalIds ||
                            item.assignment.deadline == null || item.assignment.deadline >= baseline
                    }
                    .distinctBy { TodoSource.TRONCLASS.name to it.assignment.externalId }
                val todos = eligibleAssignments.map { item ->
                    TodoEntity(
                        title = item.assignment.title,
                        description = item.assignment.description,
                        courseId = item.matchedTronCourseId,
                        source = TodoSource.TRONCLASS.name,
                        deadline = item.assignment.deadline,
                        createdTime = candidateBaselineAt,
                        updatedTime = candidateBaselineAt,
                        externalId = item.assignment.externalId,
                    )
                }

                if (baselineWasCreated) {
                    // The exam endpoint may run after homework creates the shared baseline.
                    // Do not erase an existing exam before its own update can be evaluated.
                    val incomingIds = todos.mapNotNullTo(mutableSetOf()) { it.externalId }
                    val preservedIds = existingExternalIds.filter { it.startsWith("exam:") || it in incomingIds }
                    if (preservedIds.isEmpty()) {
                        todoDao.deleteTronClassTodosBefore(TodoSource.TRONCLASS.name, baseline)
                    } else {
                        // Do not delete a row being updated: upsert must retain its local state.
                        todoDao.deleteTronClassTodosBeforeExceptExternalIds(
                            TodoSource.TRONCLASS.name, baseline, preservedIds,
                        )
                    }
                }
                if (knownInactiveCourseIds.isNotEmpty()) {
                    todoDao.deleteTronClassTodosForCourses(
                        TodoSource.TRONCLASS.name,
                        knownInactiveCourseIds.toList(),
                    )
                }
                // Commit and reconcile only after every course/page parsed successfully.
                todoDao.upsertExternalTodos(todos)
                if (activeCourseIds.isNotEmpty()) {
                    todoDao.getIncompleteExternalTodosForCourses(
                        TodoSource.TRONCLASS.name,
                        activeCourseIds.toList(),
                    ).forEach { existing ->
                        val externalId = existing.externalId
                        val deadline = existing.deadline
                        if (externalId != null &&
                            !externalId.startsWith("exam:") &&
                            externalId !in assignmentIdsInSnapshot &&
                            deadline != null &&
                            deadline > candidateBaselineAt
                        ) {
                            todoDao.updateCompletedById(
                                existing.id,
                                completed = true,
                                updatedTime = candidateBaselineAt,
                            )
                        }
                    }
                }
                TronResult.Success(todos.size)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            TronResult.Error(TronClassError.StorageError)
        }
    }

    private suspend fun fetchAllPages(courseId: Long): TronResult<List<TronAssignment>> {
        val all = ArrayList<TronAssignment>()
        var page = 1
        var expectedPages: Int? = null
        var requiresPageNumber = false
        while (page <= MAX_PAGES) {
            val response = try {
                api.getHomeworkActivities(courseId, page, pageSize)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                return TronResult.Error(TronClassError.NetworkError)
            } catch (_: Exception) {
                return TronResult.Error(TronClassError.ResponseFormatChanged)
            }
            if (response.code() == 401 || response.code() == 403) {
                return TronResult.Error(TronClassError.Unauthorized)
            }
            if (!response.isSuccessful) {
                return TronResult.Error(TronClassError.ServerError(response.code()))
            }
            val body = response.body()
                ?: return TronResult.Error(TronClassError.ResponseFormatChanged)
            val reportedPage = body.page
            val reportedPages = body.pages
            val effectivePageSize = body.pageSize ?: pageSize
            val emptyZeroPageSnapshot = page == 1 && reportedPages == 0 &&
                body.homeworkActivities?.isEmpty() == true
            if ((reportedPage != null && reportedPage != page) ||
                (requiresPageNumber && reportedPage == null) ||
                (reportedPages != null && !emptyZeroPageSnapshot && (reportedPages !in 1..MAX_PAGES || page > reportedPages)) ||
                (expectedPages != null && reportedPages != expectedPages) ||
                (effectivePageSize <= 0 && !emptyZeroPageSnapshot)
            ) {
                return TronResult.Error(TronClassError.ResponseFormatChanged)
            }
            requiresPageNumber = requiresPageNumber || reportedPage != null
            expectedPages = reportedPages
            when (val parsed = parser.parse(body)) {
                is TronResult.Error -> return parsed
                is TronResult.Success -> {
                    val totalPages = parsed.value.pages
                    if (totalPages != null && totalPages > 1 && parsed.value.assignments.isEmpty()) {
                        return TronResult.Error(TronClassError.ResponseFormatChanged)
                    }
                    all += parsed.value.assignments
                    if ((totalPages != null && page >= totalPages) ||
                        (totalPages == null && parsed.value.assignments.size < effectivePageSize)
                    ) {
                        return TronResult.Success(all)
                    }
                }
            }
            page++
        }
        return TronResult.Error(TronClassError.ResponseFormatChanged)
    }

    private data class TronAssignmentWithCourse(
        val assignment: TronAssignment,
        val matchedTronCourseId: Long?,
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_CONCURRENT_COURSE_REQUESTS = 4
        private const val MAX_PAGES = 1000
    }
}
