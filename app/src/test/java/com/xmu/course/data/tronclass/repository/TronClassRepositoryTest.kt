package com.xmu.course.data.tronclass.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseDto
import com.xmu.course.data.tronclass.api.TronCoursesResponseDto
import com.xmu.course.data.tronclass.api.TronCourseParser
import com.xmu.course.data.tronclass.api.TronSemesterDto
import com.xmu.course.data.tronclass.api.TronSemestersResponseDto
import com.xmu.course.data.tronclass.assignment.AssignmentPageDto
import com.xmu.course.data.tronclass.assignment.AssignmentRepositoryContract
import com.xmu.course.data.tronclass.auth.TronSession
import com.xmu.course.data.tronclass.todo.OfficialTodoResponseDto
import com.xmu.course.data.tronclass.todo.OfficialTodoRepositoryContract
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.tronclass.model.TronSyncState
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TronClassRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var session: TronSession

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        session = TronSession(
            sessionCookieName = "verified-session",
            sessionId = "synthetic-session-value",
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `同步成功时合并课程并保留历史课程`() = runTest {
        val old = TronCourseEntity(
            tronCourseId = 1L,
            name = "旧课程",
            semester = "旧学期",
            instructor = "旧教师",
            updatedTime = 1L,
        )
        db.tronCourseDao().insertAll(listOf(old))
        val api = FakeApi(
            response = Response.success(
                TronCoursesResponseDto(
                    listOf(TronCourseDto(2L, "新课程", TronSemesterDto(null, "新学期", null), emptyList(), null)),
                ),
            ),
        )
        val repository = repository(api)

        val result = repository.syncCourses()

        assertTrue(result is TronResult.Success)
        assertEquals(2, db.tronCourseDao().getAll().size)
        assertTrue(db.tronCourseDao().getAll().any { it.tronCourseId == 1L && it.semester == "旧学期" })
        assertTrue(db.tronCourseDao().getAll().any { it.tronCourseId == 2L && it.semester == "新学期" })
    }

    @Test
    fun `API失败时保留旧课程`() = runTest {
        val old = TronCourseEntity(
            tronCourseId = 1L,
            name = "旧课程",
            semester = "旧学期",
            instructor = "旧教师",
            updatedTime = 1L,
        )
        db.tronCourseDao().insertAll(listOf(old))
        val repository = repository(FakeApi(failure = IOException("synthetic network failure")))

        val result = repository.syncCourses()

        assertEquals(TronClassError.NetworkError, (result as TronResult.Error).error)
        assertEquals(listOf(old.copy(id = 1L)), db.tronCourseDao().getAll())
    }

    @Test
    fun `重复同步按远端ID更新而不增加课程行`() = runTest {
        db.tronCourseDao().insertAll(
            listOf(
                TronCourseEntity(
                    tronCourseId = 7L,
                    name = "旧名称",
                    semester = "旧学期",
                    instructor = "旧教师",
                    updatedTime = 1L,
                ),
            ),
        )
        val repository = repository(
            FakeApi(
                response = Response.success(
                    TronCoursesResponseDto(
                        listOf(TronCourseDto(7L, "新名称", TronSemesterDto(null, "新学期", null), emptyList(), null)),
                    ),
                ),
            ),
        )

        assertTrue(repository.syncCourses() is TronResult.Success)

        val rows = db.tronCourseDao().getAll().filter { it.tronCourseId == 7L }
        assertEquals(1, rows.size)
        assertEquals("新名称", rows.single().name)
        assertEquals("新学期", rows.single().semester)
        assertEquals(9_000L, rows.single().updatedTime)
    }

    @Test
    fun `401映射为SessionExpired并保留旧课程`() = runTest {
        val old = seedOldCourse()
        val repository = repository(
            FakeApi(response = Response.error(401, ResponseBody.create(null, "synthetic unauthorized"))),
        )

        val result = repository.syncCoursesState()

        assertEquals(TronSyncState.SessionExpired, result)
        assertEquals(listOf(old.copy(id = 1L)), db.tronCourseDao().getAll())
    }

    @Test
    fun `403映射为SessionExpired并保留旧课程`() = runTest {
        val old = seedOldCourse()
        val repository = repository(
            FakeApi(response = Response.error(403, ResponseBody.create(null, "synthetic forbidden"))),
        )

        val result = repository.syncCoursesState()

        assertEquals(TronSyncState.SessionExpired, result)
        assertEquals(listOf(old.copy(id = 1L)), db.tronCourseDao().getAll())
    }

    @Test
    fun `Parser失败时数据库不变化`() = runTest {
        val old = seedOldCourse()
        val repository = repository(
            FakeApi(response = Response.success(TronCoursesResponseDto(listOf(TronCourseDto(null, "无ID", null, emptyList(), null))))),
        )

        val result = repository.syncCourses()

        assertEquals(TronClassError.ParseError(com.xmu.course.data.tronclass.model.ParseErrorReason.MissingCourseId), (result as TronResult.Error).error)
        assertEquals(listOf(old.copy(id = 1L)), db.tronCourseDao().getAll())
    }

    @Test
    fun `Room写入异常时事务回滚并保留旧课程`() = runTest {
        val old = seedOldCourse()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_tron_course_insert " +
                "BEFORE INSERT ON tron_courses WHEN NEW.tronCourseId = 2 " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic storage failure'); END",
        )
        val repository = repository(
            FakeApi(response = Response.success(TronCoursesResponseDto(listOf(TronCourseDto(2L, "新课程", TronSemesterDto(null, "新学期", null), emptyList(), null))))),
        )

        val result = repository.syncCourses()

        assertEquals(TronClassError.StorageError, (result as TronResult.Error).error)
        assertEquals(listOf(old.copy(id = 1L)), db.tronCourseDao().getAll())
    }

    @Test
    fun `Room变更通过Repository Flow可观察`() = runTest {
        val repository = repository(FakeApi(response = Response.success(TronCoursesResponseDto(emptyList()))))

        assertTrue(repository.observeCourses().first().isEmpty())
        db.tronCourseDao().insertAll(listOf(seedOldCourse()))

        assertEquals(1, repository.observeCourses().first().size)
    }

    @Test
    fun `缺少会话时不调用API并保留旧课程`() = runTest {
        val old = TronCourseEntity(
            tronCourseId = 1L,
            name = "旧课程",
            semester = "旧学期",
            instructor = "旧教师",
            updatedTime = 1L,
        )
        db.tronCourseDao().insertAll(listOf(old))
        val api = FakeApi(response = Response.success(TronCoursesResponseDto(emptyList())))
        val repository = TronClassRepository(
            api = api,
            sessionProvider = SessionProvider { null },
            courseDao = db.tronCourseDao(),
            parser = TronCourseParser { 9_000L },
        )

        val result = repository.syncCourses()

        assertEquals(TronClassError.SessionMissing, (result as TronResult.Error).error)
        assertTrue(!api.called)
        assertEquals(listOf(old.copy(id = 1L)), db.tronCourseDao().getAll())
    }

    @Test
    fun `组合待办同步汇总homework和official exam数量`() = runTest {
        val repository = TronClassRepository(
            api = FakeApi(),
            sessionProvider = SessionProvider { session },
            courseDao = db.tronCourseDao(),
            assignmentRepository = object : AssignmentRepositoryContract {
                override suspend fun syncAssignments(): TronResult<Int> = TronResult.Success(2)
            },
            officialTodoRepository = object : OfficialTodoRepositoryContract {
                override suspend fun syncExamTodos(): TronResult<Int> = TronResult.Success(3)
            },
        )

        assertEquals(
            TronResult.Success(TronTodoSyncSummary(2, 3)),
            repository.syncTodoSources(),
        )
    }

    @Test
    fun `组合待办同步优先报告认证失败`() = runTest {
        val repository = TronClassRepository(
            api = FakeApi(),
            sessionProvider = SessionProvider { session },
            courseDao = db.tronCourseDao(),
            assignmentRepository = object : AssignmentRepositoryContract {
                override suspend fun syncAssignments(): TronResult<Int> =
                    TronResult.Error(TronClassError.NetworkError)
            },
            officialTodoRepository = object : OfficialTodoRepositoryContract {
                override suspend fun syncExamTodos(): TronResult<Int> =
                    TronResult.Error(TronClassError.Unauthorized)
            },
        )

        assertEquals(
            TronClassError.Unauthorized,
            (repository.syncTodoSources() as TronResult.Error).error,
        )
    }

    private fun repository(api: FakeApi): TronClassRepository = TronClassRepository(
        api = api,
        sessionProvider = SessionProvider { session },
        courseDao = db.tronCourseDao(),
        parser = TronCourseParser { 9_000L },
        clock = { 2_000L },
    )

    private suspend fun seedOldCourse(): TronCourseEntity {
        val old = TronCourseEntity(
            tronCourseId = 1L,
            name = "旧课程",
            semester = "旧学期",
            instructor = "旧教师",
            updatedTime = 1L,
        )
        db.tronCourseDao().insertAll(listOf(old))
        return old
    }

    private class FakeApi(
        private val response: Response<TronCoursesResponseDto>? = null,
        private val failure: IOException? = null,
    ) : TronClassApiService {
        var called = false

        override suspend fun getMySemesters(): Response<TronSemestersResponseDto> =
            error("my-semesters is not used by this task")

        override suspend fun getMyCourses(): Response<TronCoursesResponseDto> {
            called = true
            failure?.let { throw it }
            return response ?: error("missing fake response")
        }

        override suspend fun getHomeworkActivities(
            courseId: Long,
            page: Int,
            pageSize: Int,
        ): Response<AssignmentPageDto> = error("assignments are not used by this test")

        override suspend fun getOfficialTodos(): Response<OfficialTodoResponseDto> =
            Response.success(OfficialTodoResponseDto(emptyList()))
    }
}
