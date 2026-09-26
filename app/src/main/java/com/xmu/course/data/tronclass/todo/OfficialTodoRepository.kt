package com.xmu.course.data.tronclass.todo

import androidx.room.withTransaction
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseParser
import com.xmu.course.data.tronclass.api.TronSemesterParser
import com.xmu.course.data.tronclass.assignment.TronTodoSyncMetadataEntity
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.todo.TodoDao
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OfficialTodoRepositoryContract {
    suspend fun syncExamTodos(): TronResult<Int>
}

/** `/api/todos` → exam 候选 → 当前学期过滤 → 现有 Todo 表的安全同步。 */
class OfficialTodoRepository(
    private val api: TronClassApiService,
    private val sessionProvider: SessionProvider,
    private val todoDao: TodoDao,
    private val database: AppDatabase,
    private val parser: OfficialTodoParser = OfficialTodoParser(),
    private val semesterParser: TronSemesterParser = TronSemesterParser(),
    private val courseParser: TronCourseParser = TronCourseParser(),
    private val clock: () -> Long = System::currentTimeMillis,
) : OfficialTodoRepositoryContract {

    override suspend fun syncExamTodos(): TronResult<Int> = withContext(Dispatchers.IO) {
        syncOnIo()
    }

    private suspend fun syncOnIo(): TronResult<Int> {
        val session = runCatching { sessionProvider.getSession() }.getOrNull()
            ?: return TronResult.Error(TronClassError.SessionMissing)
        if (session.isExpired(clock())) {
            return TronResult.Error(TronClassError.SessionMissing)
        }

        val semestersResponse = try {
            api.getMySemesters()
        } catch (_: IOException) {
            return TronResult.Error(TronClassError.NetworkError)
        } catch (_: Exception) {
            return TronResult.Error(TronClassError.ResponseFormatChanged)
        }
        when {
            semestersResponse.code() == 401 || semestersResponse.code() == 403 ->
                return TronResult.Error(TronClassError.Unauthorized)
            !semestersResponse.isSuccessful ->
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
        } catch (_: IOException) {
            return TronResult.Error(TronClassError.NetworkError)
        } catch (_: Exception) {
            return TronResult.Error(TronClassError.ResponseFormatChanged)
        }
        when {
            coursesResponse.code() == 401 || coursesResponse.code() == 403 ->
                return TronResult.Error(TronClassError.Unauthorized)
            !coursesResponse.isSuccessful ->
                return TronResult.Error(TronClassError.ServerError(coursesResponse.code()))
        }
        val coursesBody = coursesResponse.body()
            ?: return TronResult.Error(TronClassError.ResponseFormatChanged)
        when (val parsedCourses = courseParser.parse(coursesBody)) {
            is TronResult.Error -> return parsedCourses
            is TronResult.Success -> Unit
        }
        val activeCourseIds = courseParser.activeCourseIds(coursesBody, activeSemesterId)

        val todosResponse = try {
            api.getOfficialTodos()
        } catch (_: IOException) {
            return TronResult.Error(TronClassError.NetworkError)
        } catch (_: Exception) {
            return TronResult.Error(TronClassError.ResponseFormatChanged)
        }
        when {
            todosResponse.code() == 401 || todosResponse.code() == 403 ->
                return TronResult.Error(TronClassError.Unauthorized)
            !todosResponse.isSuccessful ->
                return TronResult.Error(TronClassError.ServerError(todosResponse.code()))
        }
        val todosBody = todosResponse.body()
            ?: return TronResult.Error(TronClassError.ResponseFormatChanged)
        val candidates = when (val parsed = parser.parse(todosBody)) {
            is TronResult.Error -> return parsed
            is TronResult.Success -> parsed.value
        }

        // 只在当前学期资格确认后生成本地实体；非当前学期条目不进入写库集合。
        val currentSemesterCandidates = candidates
            .filter { it.courseId in activeCourseIds }
            .distinctBy { it.remoteId }
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
                val todos = currentSemesterCandidates
                    .filter { it.deadline == null || it.deadline >= baseline }
                    .map { candidate ->
                        TodoEntity(
                            title = candidate.title,
                            description = "",
                            courseId = candidate.courseId,
                            source = TodoSource.TRONCLASS.name,
                            deadline = candidate.deadline,
                            createdTime = candidateBaselineAt,
                            updatedTime = candidateBaselineAt,
                            externalId = "exam:${candidate.remoteId}",
                        )
                    }
                if (baselineWasCreated) {
                    todoDao.deleteTronClassTodosBefore(TodoSource.TRONCLASS.name, baseline)
                }
                // 不根据本次缺失项删除旧数据；官方列表的完整权威语义尚未承诺。
                todoDao.upsertExternalTodos(todos)
                TronResult.Success(todos.size)
            }
        }.getOrElse { TronResult.Error(TronClassError.StorageError) }
    }
}
