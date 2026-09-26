package com.xmu.course.data.tronclass.repository

import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseParser
import com.xmu.course.data.tronclass.auth.TronSessionStore
import com.xmu.course.data.tronclass.assignment.AssignmentRepositoryContract
import com.xmu.course.data.tronclass.todo.OfficialTodoRepositoryContract
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.tronclass.model.TronSyncState
import com.xmu.course.data.tronclass.model.toSyncState
import java.io.IOException
import kotlinx.coroutines.flow.Flow

interface TronClassRepositoryContract {
    suspend fun syncCourses(): TronResult<List<TronCourseEntity>>

    suspend fun syncCoursesState(): TronSyncState

    fun observeCourses(): Flow<List<TronCourseEntity>>

    suspend fun clearCourses(): TronResult<Unit>

    fun hasSession(): Boolean

    suspend fun logout(): TronResult<Unit>

    /** 默认实现保持旧调用方兼容；实际应用由工厂注入 AssignmentRepository。 */
    suspend fun syncAssignments(): TronResult<Int> =
        TronResult.Error(TronClassError.AuthRequired)

    /** homework 与官方 exam 待办的独立同步汇总；失败不会撤销已成功的数据源缓存。 */
    suspend fun syncTodoSources(): TronResult<TronTodoSyncSummary> = when (val assignments = syncAssignments()) {
        is TronResult.Error -> assignments
        is TronResult.Success -> TronResult.Success(TronTodoSyncSummary(assignments.value, 0))
    }
}

data class TronTodoSyncSummary(
    val assignmentCount: Int,
    val officialExamTodoCount: Int,
) {
    val totalCount: Int get() = assignmentCount + officialExamTodoCount
}

/** TronClass API → Parser → Room 的基础编排，不向 UI 暴露 Retrofit 异常。 */
class TronClassRepository(
    private val api: TronClassApiService,
    private val sessionProvider: SessionProvider,
    private val courseDao: TronCourseDao,
    private val parser: TronCourseParser = TronCourseParser(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sessionStore: TronSessionStore? = null,
    private val assignmentRepository: AssignmentRepositoryContract? = null,
    private val officialTodoRepository: OfficialTodoRepositoryContract? = null,
) : TronClassRepositoryContract {
    /** 保留底层结果接口，供数据层测试和后续更细粒度编排使用。 */
    override suspend fun syncCourses(): TronResult<List<TronCourseEntity>> {
        val session = runCatching { sessionProvider.getSession() }.getOrNull()
            ?: return TronResult.Error(TronClassError.SessionMissing)
        if (session.isExpired(clock())) {
            return TronResult.Error(TronClassError.SessionMissing)
        }

        val response = try {
            api.getMyCourses()
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

        return when (val parsed = parser.parse(body)) {
            is TronResult.Error -> parsed
            is TronResult.Success -> runCatching {
                // 解析完整成功后合并写入，保留已经缓存的历史学期；网络与解析失败不会触碰旧缓存。
                courseDao.upsertAllPreservingHistory(parsed.value)
                TronResult.Success(parsed.value)
            }.getOrElse {
                TronResult.Error(TronClassError.StorageError)
            }
        }
    }

    /** 面向上层的安全同步状态接口；401 映射为 SessionExpired，缺少会话映射为 AuthRequired。 */
    override suspend fun syncCoursesState(): TronSyncState =
        syncCourses().toSyncState { it.size }

    override fun observeCourses(): Flow<List<TronCourseEntity>> = courseDao.observeAll()

    override suspend fun clearCourses(): TronResult<Unit> = runCatching {
        courseDao.deleteAll()
        TronResult.Success(Unit)
    }.getOrElse {
        TronResult.Error(TronClassError.StorageError)
    }

    override fun hasSession(): Boolean = runCatching {
        val session = sessionProvider.getSession() ?: return false
        !session.isExpired(clock())
    }.getOrDefault(false)

    override suspend fun logout(): TronResult<Unit> = runCatching {
        // 仅清理 TronClass 的加密会话和课程表；WebView Cookie 由 auth Activity 负责清理。
        sessionStore?.clearSession()
            ?: error("TronClass session store is not configured")
        when (val result = clearCourses()) {
            is TronResult.Success -> TronResult.Success(Unit)
            is TronResult.Error -> result
        }
    }.getOrElse {
        TronResult.Error(TronClassError.StorageError)
    }

    override suspend fun syncAssignments(): TronResult<Int> =
        assignmentRepository?.syncAssignments()
            ?: TronResult.Error(TronClassError.AuthRequired)

    override suspend fun syncTodoSources(): TronResult<TronTodoSyncSummary> {
        val assignments = syncAssignments()
        val official = officialTodoRepository?.syncExamTodos()
            ?: TronResult.Success(0)
        val errors = listOfNotNull(
            (assignments as? TronResult.Error)?.error,
            (official as? TronResult.Error)?.error,
        )
        val error = errors.firstOrNull(::isAuthenticationError) ?: errors.firstOrNull()
        if (error != null) return TronResult.Error(error)
        return TronResult.Success(
            TronTodoSyncSummary(
                assignmentCount = (assignments as TronResult.Success<Int>).value,
                officialExamTodoCount = (official as TronResult.Success<Int>).value,
            ),
        )
    }

    private fun isAuthenticationError(error: TronClassError): Boolean =
        error == TronClassError.Unauthorized ||
            error == TronClassError.SessionMissing ||
            error == TronClassError.AuthRequired
}
