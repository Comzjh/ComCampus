package com.xmu.course.data.tronclass.todo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseDto
import com.xmu.course.data.tronclass.api.TronCoursesResponseDto
import com.xmu.course.data.tronclass.api.TronSemesterDto
import com.xmu.course.data.tronclass.api.TronSemestersResponseDto
import com.xmu.course.data.tronclass.assignment.AssignmentPageDto
import com.xmu.course.data.tronclass.auth.TronSession
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialTodoRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var session: TronSession

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        session = TronSession("session", "synthetic-session", 1L, 1L)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `首次同步导入exam并使用命名空间externalId`() = runTest {
        val result = repository(FakeApi()).syncExamTodos()

        assertEquals(TronResult.Success(1), result)
        val todo = db.todoDao().getAll().single()
        assertEquals("exam:101", todo.externalId)
        assertEquals(TodoSource.TRONCLASS.name, todo.source)
        assertEquals(9001L, todo.courseId)
    }

    @Test
    fun `重复同步同一exam只更新不重复`() = runTest {
        val api = FakeApi()
        val repo = repository(api)
        repo.syncExamTodos()
        api.todoResponse = Response.success(todoResponse(title = "修改后的标题"))

        assertEquals(TronResult.Success(1), repo.syncExamTodos())
        val rows = db.todoDao().getAll()
        assertEquals(1, rows.size)
        assertEquals("修改后的标题", rows.single().title)
    }

    @Test
    fun `exam与原有homework数字id相同也不冲突`() = runTest {
        db.todoDao().insert(
            TodoEntity(
                title = "作业来源",
                description = "",
                courseId = 9001L,
                source = TodoSource.TRONCLASS.name,
                deadline = null,
                createdTime = 1L,
                updatedTime = 1L,
                externalId = "101",
            ),
        )

        repository(FakeApi()).syncExamTodos()

        val ids = db.todoDao().getAll().mapNotNull { it.externalId }.toSet()
        assertEquals(setOf("101", "exam:101"), ids)
    }

    @Test
    fun `homework和未知类型不会通过新链路导入`() = runTest {
        val response = Response.success(
            OfficialTodoResponseDto(
                listOf(
                    todo(101, type = "homework"),
                    todo(102, type = "quiz"),
                    todo(103, type = "interaction"),
                    todo(104, type = "classroom_exam"),
                    todo(105, type = "exam"),
                ),
            ),
        )

        assertEquals(TronResult.Success(1), repository(FakeApi(todoResponse = response)).syncExamTodos())
        assertEquals("exam:105", db.todoDao().getAll().single().externalId)
    }

    @Test
    fun `只导入当前学期课程`() = runTest {
        val courses = TronCoursesResponseDto(
            listOf(
                course(9001L, 10L),
                course(9002L, 11L),
            ),
        )
        val response = Response.success(
            OfficialTodoResponseDto(listOf(todo(101, courseId = 9001L), todo(102, courseId = 9002L))),
        )

        assertEquals(
            TronResult.Success(1),
            repository(FakeApi(coursesResponse = Response.success(courses), todoResponse = response)).syncExamTodos(),
        )
        assertEquals("exam:101", db.todoDao().getAll().single().externalId)
    }

    @Test
    fun `baseline之前的exam不导入`() = runTest {
        val baseline = 10_000L
        val old = todo(101, endTime = "1970-01-01T00:00:09Z")

        assertEquals(
            TronResult.Success(0),
            repository(FakeApi(todoResponse = Response.success(OfficialTodoResponseDto(listOf(old)))), clock = { baseline })
                .syncExamTodos(),
        )
        assertTrue(db.todoDao().getAll().isEmpty())
    }

    @Test
    fun `本地completed状态在远端exam同步后保留`() = runTest {
        db.todoDao().insert(
            TodoEntity(
                title = "旧标题",
                description = "",
                courseId = 9001L,
                source = TodoSource.TRONCLASS.name,
                deadline = null,
                completed = true,
                createdTime = 1L,
                updatedTime = 1L,
                externalId = "exam:101",
            ),
        )

        repository(FakeApi(todoResponse = Response.success(todoResponse(title = "新标题")))).syncExamTodos()

        val saved = db.todoDao().getAll().single()
        assertEquals("新标题", saved.title)
        assertTrue(saved.completed)
    }

    @Test
    fun `无截止时间exam正常保存`() = runTest {
        val response = Response.success(OfficialTodoResponseDto(listOf(todo(101, endTime = null))))

        assertEquals(TronResult.Success(1), repository(FakeApi(todoResponse = response)).syncExamTodos())
        assertEquals(null, db.todoDao().getAll().single().deadline)
    }

    @Test
    fun `网络失败保留旧exam缓存`() = runTest {
        val old = TodoEntity(
            title = "旧缓存",
            description = "",
            courseId = 9001L,
            source = TodoSource.TRONCLASS.name,
            deadline = null,
            createdTime = 1L,
            updatedTime = 1L,
            externalId = "exam:999",
        )
        db.todoDao().insert(old)

        val result = repository(FakeApi(failure = IOException("synthetic"))).syncExamTodos()

        assertEquals(TronClassError.NetworkError, (result as TronResult.Error).error)
        assertEquals(old.copy(id = 1L), db.todoDao().getAll().single())
    }

    @Test
    fun `401保留旧exam缓存并返回未授权`() = runTest {
        val old = seedOldExternalTodo()
        val result = repository(
            FakeApi(todoResponse = Response.error(401, ResponseBody.create(null, "synthetic"))),
        ).syncExamTodos()

        assertEquals(TronClassError.Unauthorized, (result as TronResult.Error).error)
        assertEquals(old.copy(id = 1L), db.todoDao().getAll().single())
    }

    @Test
    fun `403保留旧exam缓存并返回未授权`() = runTest {
        val old = seedOldExternalTodo()
        val result = repository(
            FakeApi(todoResponse = Response.error(403, ResponseBody.create(null, "synthetic"))),
        ).syncExamTodos()

        assertEquals(TronClassError.Unauthorized, (result as TronResult.Error).error)
        assertEquals(old.copy(id = 1L), db.todoDao().getAll().single())
    }

    @Test
    fun `解析失败保留旧exam缓存`() = runTest {
        val old = seedOldExternalTodo()
        val result = repository(
            FakeApi(todoResponse = Response.success(OfficialTodoResponseDto(listOf(todo(0))))),
        ).syncExamTodos()

        assertTrue(result is TronResult.Error)
        assertEquals(old.copy(id = 1L), db.todoDao().getAll().single())
    }

    @Test
    fun `LOCAL待办不受baseline清理影响`() = runTest {
        val local = TodoEntity(
            title = "本地待办",
            description = "",
            courseId = null,
            source = TodoSource.LOCAL.name,
            deadline = 1L,
            createdTime = 1L,
            updatedTime = 1L,
            externalId = null,
        )
        db.todoDao().insert(local)

        repository(FakeApi()).syncExamTodos()

        assertTrue(db.todoDao().getAll().any { it.source == TodoSource.LOCAL.name })
    }

    @Test
    fun `远端缺失exam不触发删除`() = runTest {
        val old = seedOldExternalTodo()
        val response = Response.success(OfficialTodoResponseDto(emptyList()))

        assertEquals(TronResult.Success(0), repository(FakeApi(todoResponse = response)).syncExamTodos())
        assertEquals(old.copy(id = 1L), db.todoDao().getAll().single())
    }

    @Test
    fun `数据库事务失败时baseline清理与写入一起回滚`() = runTest {
        val baseline = 1_000L
        val old = TodoEntity(
            title = "legacy历史畅课待办",
            description = "",
            courseId = 9001L,
            source = TodoSource.TRONCLASS.name,
            deadline = 1L,
            createdTime = 1L,
            updatedTime = 1L,
            externalId = null,
        )
        db.todoDao().insert(old)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_official_exam_insert " +
                "BEFORE INSERT ON todo_items WHEN NEW.externalId = 'exam:101' " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic storage failure'); END",
        )

        val result = repository(FakeApi(), clock = { baseline }).syncExamTodos()

        assertEquals(TronClassError.StorageError, (result as TronResult.Error).error)
        assertEquals(old.copy(id = 1L), db.todoDao().getAll().single())
        assertEquals(null, db.tronTodoSyncMetadataDao().get())
    }

    @Test
    fun `会话缺失不请求API`() = runTest {
        val api = FakeApi()
        val result = OfficialTodoRepository(
            api = api,
            sessionProvider = SessionProvider { null },
            todoDao = db.todoDao(),
            database = db,
        ).syncExamTodos()

        assertEquals(TronClassError.SessionMissing, (result as TronResult.Error).error)
        assertFalse(api.called)
    }

    private fun repository(api: FakeApi, clock: () -> Long = { 1_000L }) = OfficialTodoRepository(
        api = api,
        sessionProvider = SessionProvider { session },
        todoDao = db.todoDao(),
        database = db,
        clock = clock,
    )

    private suspend fun seedOldExternalTodo(): TodoEntity {
        val old = TodoEntity(
            title = "旧缓存",
            description = "",
            courseId = 9001L,
            source = TodoSource.TRONCLASS.name,
            deadline = null,
            createdTime = 1L,
            updatedTime = 1L,
            externalId = "exam:999",
        )
        db.todoDao().insert(old)
        return old
    }

    private fun todo(
        id: Long,
        courseId: Long = 9001L,
        type: String = "exam",
        endTime: String? = "2026-09-20T15:59:00Z",
    ) = OfficialTodoItemDto(
        id = id,
        title = "任务$id",
        courseId = courseId,
        courseName = "测试课程",
        courseCode = "P01",
        endTime = endTime,
        type = type,
        isStudent = true,
        isLocked = false,
    )

    private fun todoResponse(title: String = "任务101") = OfficialTodoResponseDto(
        listOf(todo(101).copy(title = title)),
    )

    private fun course(id: Long, semesterId: Long) = TronCourseDto(
        id = id,
        name = "课程$id",
        semester = TronSemesterDto(null, null, null, id = semesterId, sort = 1),
        instructors = emptyList(),
        instructor = null,
    )

    private class FakeApi(
        var semestersResponse: Response<TronSemestersResponseDto> = Response.success(
            TronSemestersResponseDto(
                listOf(TronSemesterDto(null, null, null, id = 10L, sort = 1, isActive = true)),
            ),
        ),
        var coursesResponse: Response<TronCoursesResponseDto> = Response.success(
            TronCoursesResponseDto(listOf(courseStatic(9001L, 10L))),
        ),
        var todoResponse: Response<OfficialTodoResponseDto> = Response.success(
            OfficialTodoResponseDto(listOf(todoStatic(101))),
        ),
        private val failure: IOException? = null,
    ) : TronClassApiService {
        var called = false

        override suspend fun getMySemesters(): Response<TronSemestersResponseDto> = semestersResponse

        override suspend fun getMyCourses(): Response<TronCoursesResponseDto> = coursesResponse

        override suspend fun getHomeworkActivities(
            courseId: Long,
            page: Int,
            pageSize: Int,
        ): Response<AssignmentPageDto> = error("homework is not used")

        override suspend fun getOfficialTodos(): Response<OfficialTodoResponseDto> {
            called = true
            failure?.let { throw it }
            return todoResponse
        }
    }

    private companion object {
        fun todoStatic(id: Long) = OfficialTodoItemDto(
            id = id,
            title = "任务$id",
            courseId = 9001L,
            courseName = "测试课程",
            courseCode = "P01",
            endTime = "2026-09-20T15:59:00Z",
            type = "exam",
            isStudent = true,
            isLocked = false,
        )

        fun courseStatic(id: Long, semesterId: Long) = TronCourseDto(
            id = id,
            name = "课程$id",
            semester = TronSemesterDto(null, null, null, id = semesterId, sort = 1),
            instructors = emptyList(),
            instructor = null,
        )
    }
}
