package com.xmu.course.data.tronclass.assignment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.CourseEntity
import com.xmu.course.data.local.SemesterEntity
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseDto
import com.xmu.course.data.tronclass.api.TronCoursesResponseDto
import com.xmu.course.data.tronclass.api.TronSemesterDto
import com.xmu.course.data.tronclass.api.TronSemestersResponseDto
import com.xmu.course.data.tronclass.todo.OfficialTodoResponseDto
import com.xmu.course.data.tronclass.auth.TronSession
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
class AssignmentSyncTest {
    private lateinit var db: AppDatabase
    private lateinit var tronCourse: TronCourseEntity
    private val sessionProvider = SessionProvider {
        TronSession(sessionCookieName = "session", sessionId = "s", createdAt = 1L, updatedAt = 1L)
    }

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.semesterDao().insert(SemesterEntity(id = 1L, code = "20261", name = "秋季"))
        tronCourse = TronCourseEntity(
            tronCourseId = 9001L,
            name = "Python程序设计",
            semester = "2026-2027秋季",
            instructor = "教师",
            updatedTime = 1L,
        )
        db.tronCourseDao().insertAll(listOf(tronCourse))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `作业新增并通过CourseMatcher绑定畅课课程`() = runTest {
        db.courseDao().insert(localCourse("Python程序设计（11）"))
        val repository = repository(FakeApi(Response.success(page(AssignmentDto(123L, "测试作业", "完成练习", 9001L, "2026-09-20T23:59:00Z", null)))))

        val result = repository.syncAssignments()

        assertEquals(TronResult.Success(1), result)
        val todo = db.todoDao().getAll().first { it.externalId == "123" }
        assertEquals("123", todo.externalId)
        assertEquals(TodoSource.TRONCLASS.name, todo.source)
        assertEquals(9001L, todo.courseId)
        assertEquals(Instant.parse("2026-09-20T23:59:00Z").toEpochMilli(), todo.deadline)
    }

    @Test
    fun `重复同步更新作业但保留完成状态和创建时间`() = runTest {
        db.todoDao().insert(
            TodoEntity(
                title = "旧标题", description = "旧描述", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = null, completed = true,
                createdTime = 100L, updatedTime = 100L, externalId = "123",
            ),
        )
        db.todoDao().insert(
            TodoEntity(
                title = "本地同名作业", description = "", courseId = null,
                source = TodoSource.LOCAL.name, deadline = null,
                createdTime = 100L, updatedTime = 100L,
            ),
        )
        val repository = repository(FakeApi(Response.success(page(AssignmentDto(123L, "新标题", "新描述", 9001L, null, null)))))

        repository.syncAssignments()

        val todo = db.todoDao().getAll().first { it.externalId == "123" }
        assertEquals("新标题", todo.title)
        assertEquals("新描述", todo.description)
        assertTrue(todo.completed)
        assertEquals(100L, todo.createdTime)
        assertEquals(9_000L, todo.updatedTime)
        assertEquals(2, db.todoDao().getAll().size)
        assertTrue(db.todoDao().getAll().any { it.source == TodoSource.LOCAL.name && it.title == "本地同名作业" })
    }

    @Test
    fun missingFutureHomeworkIsCompletedButOtherTodosStayUnchanged() = runTest {
        val syncTime = 10_000L
        db.todoDao().insertAll(
            listOf(
                TodoEntity(
                    title = "消失的作业", description = "", courseId = 9001L,
                    source = TodoSource.TRONCLASS.name, deadline = syncTime + 1,
                    createdTime = 1L, updatedTime = 1L, externalId = "601",
                ),
                TodoEntity(
                    title = "官方考试", description = "", courseId = 9001L,
                    source = TodoSource.TRONCLASS.name, deadline = syncTime + 1,
                    createdTime = 1L, updatedTime = 1L, externalId = "exam:602",
                ),
                TodoEntity(
                    title = "无截止作业", description = "", courseId = 9001L,
                    source = TodoSource.TRONCLASS.name, deadline = null,
                    createdTime = 1L, updatedTime = 1L, externalId = "603",
                ),
                TodoEntity(
                    title = "手动待办", description = "", courseId = 9001L,
                    source = TodoSource.LOCAL.name, deadline = syncTime + 1,
                    createdTime = 1L, updatedTime = 1L,
                ),
            ),
        )

        val result = repository(
            FakeApi(Response.success(page())),
            clock = { syncTime },
        ).syncAssignments()

        assertEquals(TronResult.Success(0), result)
        val todos = db.todoDao().getAll()
        val completedHomework = todos.single { it.externalId == "601" }
        assertTrue(completedHomework.completed)
        assertEquals(syncTime, completedHomework.updatedTime)
        assertFalse(todos.single { it.externalId == "exam:602" }.completed)
        assertFalse(todos.single { it.externalId == "603" }.completed)
        assertFalse(todos.single { it.title == "手动待办" }.completed)
    }

    @Test
    fun missingHomeworkAtOrAfterDeadlineRemainsIncomplete() = runTest {
        val syncTime = 10_000L
        db.tronTodoSyncMetadataDao().insertIfAbsent(
            TronTodoSyncMetadataEntity(importBaselineAt = 1_000L),
        )
        db.todoDao().insert(
            TodoEntity(
                title = "已逾期的作业", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = syncTime - 1,
                createdTime = 1L, updatedTime = 1L, externalId = "611",
            ),
        )

        val result = repository(
            FakeApi(Response.success(page())),
            clock = { syncTime },
        ).syncAssignments()

        assertEquals(TronResult.Success(0), result)
        val todo = db.todoDao().getAll().single()
        assertFalse(todo.completed)
        assertEquals(syncTime - 1, todo.deadline)
        assertEquals(1L, todo.updatedTime)
    }

    @Test
    fun assignmentPresentInFullSnapshotIsNotCompletedWhenBaselineFiltersImport() = runTest {
        val syncTime = 10_000L
        db.tronTodoSyncMetadataDao().insertIfAbsent(
            TronTodoSyncMetadataEntity(importBaselineAt = 5_000L),
        )
        db.todoDao().insert(
            TodoEntity(
                title = "仍存在的作业", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = 20_000L,
                createdTime = 1L, updatedTime = 1L, externalId = "621",
            ),
        )
        val api = FakeApi(
            Response.success(
                page(
                    AssignmentDto(
                        621L,
                        "基线前的作业",
                        "",
                        9001L,
                        Instant.ofEpochMilli(4_999L).toString(),
                        null,
                    ),
                ),
            ),
        )

        assertEquals(TronResult.Success(0), repository(api, clock = { syncTime }).syncAssignments())
        val todo = db.todoDao().getAll().single()
        assertFalse(todo.completed)
        assertEquals("仍存在的作业", todo.title)
    }

    @Test
    fun allPagesAreWrittenAndOldTodoIsKeptWhenParsingFails() = runTest {
        val old = TodoEntity(
            title = "旧作业", description = "", courseId = null,
            source = TodoSource.TRONCLASS.name, deadline = null,
            createdTime = 1L, updatedTime = 1L, externalId = "old",
        )
        db.todoDao().insert(old)
        val api = FakeApi(
            responses = mapOf(
                1 to Response.success(page(AssignmentDto(123L, "第一页", "", 9001L, null, null), pages = 2)),
                2 to Response.success(page(AssignmentDto(124L, "第二页", "", 9001L, null, null), pages = 2)),
            ),
        )
        val repository = repository(api, pageSize = 1)

        val result = repository.syncAssignments()

        assertEquals(TronResult.Success(2), result)
        assertEquals(setOf("old", "123", "124"), db.todoDao().getAll().mapNotNull { it.externalId }.toSet())
    }

    @Test
    fun `网络失败和401不改变已有Todo`() = runTest {
        val old = TodoEntity(
            title = "本地缓存作业", description = "", courseId = null,
            source = TodoSource.TRONCLASS.name, deadline = 10_000L,
            createdTime = 1L, updatedTime = 1L, externalId = "old",
        )
        db.todoDao().insert(old)

        val network = repository(FakeApi(failure = IOException("network"))).syncAssignments()
        assertEquals(TronClassError.NetworkError, (network as TronResult.Error).error)
        assertEquals(listOf(old.copy(id = 1L)), db.todoDao().getAll())
        assertEquals(null, db.tronTodoSyncMetadataDao().get())

        val unauthorized = repository(FakeApi(responses = mapOf(1 to Response.error(401, ResponseBody.create(null, "unauthorized"))))).syncAssignments()
        assertEquals(TronClassError.Unauthorized, (unauthorized as TronResult.Error).error)
        assertEquals(listOf(old.copy(id = 1L)), db.todoDao().getAll())
    }

    @Test
    fun `首次成功即使作业列表为空也建立baseline`() = runTest {
        val result = repository(
            FakeApi(Response.success(page())),
            clock = { 1_000L },
        ).syncAssignments()

        assertEquals(TronResult.Success(0), result)
        assertEquals(1_000L, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
        assertTrue(db.todoDao().getAll().isEmpty())
    }

    @Test
    fun `解析失败时baseline不建立`() = runTest {
        val result = repository(
            FakeApi(Response.success(page(AssignmentDto(null, "无ID", null, 9001L, null, null)))),
            clock = { 1_000L },
        ).syncAssignments()

        assertTrue(result is TronResult.Error)
        assertEquals(null, db.tronTodoSyncMetadataDao().get())
        assertTrue(db.todoDao().getAll().isEmpty())
    }

    @Test
    fun `baseline按绝对时间过滤历史并保留边界未来和无截止时间`() = runTest {
        val baseline = Instant.parse("2026-09-14T10:00:00Z").toEpochMilli()
        val api = FakeApi(
            Response.success(
                page(
                    AssignmentDto(401L, "历史作业", null, 9001L, Instant.ofEpochMilli(baseline - 1).toString(), null),
                    AssignmentDto(402L, "边界作业", null, 9001L, Instant.ofEpochMilli(baseline).toString(), null),
                    AssignmentDto(403L, "未来作业", null, 9001L, Instant.ofEpochMilli(baseline + 1).toString(), null),
                    AssignmentDto(404L, "无截止作业", null, 9001L, null, null),
                ),
            ),
        )

        val result = repository(api, clock = { baseline }).syncAssignments()

        assertEquals(TronResult.Success(3), result)
        assertEquals(setOf("402", "403", "404"), db.todoDao().getAll().mapNotNull { it.externalId }.toSet())
        assertEquals(baseline, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
    }

    @Test
    fun `首次建立baseline时清理带外部ID和legacy历史畅课Todo`() = runTest {
        val baseline = 10_000L
        db.todoDao().insert(
            TodoEntity(
                title = "历史畅课作业", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = baseline - 1,
                createdTime = 1L, updatedTime = 1L, externalId = "historical",
            ),
        )
        db.todoDao().insert(
            TodoEntity(
                title = "历史手动待办", description = "", courseId = null,
                source = TodoSource.LOCAL.name, deadline = baseline - 1,
                createdTime = 1L, updatedTime = 1L,
            ),
        )
        db.todoDao().insert(
            TodoEntity(
                title = "legacy历史畅课作业", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = baseline - 1,
                createdTime = 1L, updatedTime = 1L, externalId = null,
            ),
        )
        db.todoDao().insert(
            TodoEntity(
                title = "无截止畅课待办", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = null,
                createdTime = 1L, updatedTime = 1L, externalId = null,
            ),
        )

        val result = repository(
            FakeApi(Response.success(page(AssignmentDto(405L, "新作业", null, 9001L, null, null)))),
            clock = { baseline },
        ).syncAssignments()

        assertEquals(TronResult.Success(1), result)
        assertTrue(db.todoDao().getAll().none { it.externalId == "historical" })
        assertTrue(db.todoDao().getAll().none { it.title == "legacy历史畅课作业" })
        assertTrue(db.todoDao().getAll().any { it.source == TodoSource.LOCAL.name && it.title == "历史手动待办" })
        assertTrue(db.todoDao().getAll().any { it.externalId == null && it.title == "无截止畅课待办" })
    }

    @Test
    fun `legacy畅课Todo没有externalId也按baseline清理`() = runTest {
        val baseline = 15_000L
        db.todoDao().insert(
            TodoEntity(
                title = "legacy无外部ID作业", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = baseline - 1,
                createdTime = 1L, updatedTime = 1L, externalId = null,
            ),
        )

        val result = repository(
            FakeApi(Response.success(page(AssignmentDto(408L, "新作业", null, 9001L, null, null)))),
            clock = { baseline },
        ).syncAssignments()

        assertEquals(TronResult.Success(1), result)
        assertTrue(db.todoDao().getAll().none { it.title == "legacy无外部ID作业" })
    }

    @Test
    fun `已有baseline后续同步不移动baseline`() = runTest {
        val firstBaseline = 20_000L
        val assignment = AssignmentDto(406L, "可更新作业", null, 9001L, null, null)
        val first = repository(
            FakeApi(Response.success(page(assignment))),
            clock = { firstBaseline },
        ).syncAssignments()
        assertEquals(TronResult.Success(1), first)

        val laterBaseline = firstBaseline + 10_000L
        val second = repository(
            FakeApi(Response.success(page(assignment.copy(title = "更新后的作业")))),
            clock = { laterBaseline },
        ).syncAssignments()

        assertEquals(TronResult.Success(1), second)
        assertEquals(firstBaseline, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
        assertEquals("更新后的作业", db.todoDao().getAll().single().title)
    }

    @Test
    fun `首次事务失败时baseline和历史清理全部回滚`() = runTest {
        val baseline = 30_000L
        val old = TodoEntity(
            title = "历史畅课作业", description = "", courseId = 9001L,
            source = TodoSource.TRONCLASS.name, deadline = baseline - 1,
            createdTime = 1L, updatedTime = 1L, externalId = null,
        )
        db.todoDao().insert(old)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_baseline_todo_insert " +
                "BEFORE INSERT ON todo_items WHEN NEW.externalId = '407' " +
                "BEGIN SELECT RAISE(ABORT, 'synthetic storage failure'); END",
        )

        val result = repository(
            FakeApi(Response.success(page(AssignmentDto(407L, "新作业", null, 9001L, null, null)))),
            clock = { baseline },
        ).syncAssignments()

        assertEquals(TronClassError.StorageError, (result as TronResult.Error).error)
        assertEquals(null, db.tronTodoSyncMetadataDao().get())
        assertTrue(db.todoDao().getAll().any { it.title == "历史畅课作业" && it.externalId == null })
    }

    @Test
    fun `403按会话失效处理且不改变已有Todo`() = runTest {
        val old = TodoEntity(
            title = "缓存作业", description = "", courseId = null,
            source = TodoSource.TRONCLASS.name, deadline = null,
            createdTime = 1L, updatedTime = 1L, externalId = "old",
        )
        db.todoDao().insert(old)

        val result = repository(
            FakeApi(responses = mapOf(1 to Response.error(403, ResponseBody.create(null, "synthetic forbidden")))),
        ).syncAssignments()

        assertEquals(TronClassError.Unauthorized, (result as TronResult.Error).error)
        assertEquals(listOf(old.copy(id = 1L)), db.todoDao().getAll())
    }

    @Test
    fun `中间课程失败时整批不写入新Todo`() = runTest {
        db.tronCourseDao().insertAll(
            listOf(
                tronCourse.copy(tronCourseId = 9002L, name = "课程2"),
                tronCourse.copy(tronCourseId = 9003L, name = "课程3"),
            ),
        )
        val oldTodos = listOf(
            TodoEntity(
                title = "旧作业A", description = "", courseId = 9001L,
                source = TodoSource.TRONCLASS.name, deadline = null,
                createdTime = 1L, updatedTime = 1L, externalId = "old-a",
            ),
            TodoEntity(
                title = "旧作业B", description = "", courseId = 9002L,
                source = TodoSource.TRONCLASS.name, deadline = null,
                createdTime = 1L, updatedTime = 1L, externalId = "old-b",
            ),
        )
        db.todoDao().insertAll(oldTodos)
        val api = FakeApi(
            responsesByCourse = mapOf(
                9001L to Response.success(page(AssignmentDto(201L, "新作业A", "", 9001L, null, null))),
                9002L to Response.success(page(AssignmentDto(202L, "新作业B", "", 9002L, null, null))),
            ),
            failureCourses = setOf(9003L),
        )

        val result = repository(api).syncAssignments()

        assertEquals(TronClassError.NetworkError, (result as TronResult.Error).error)
        assertEquals(oldTodos.size, db.todoDao().getAll().size)
        assertEquals(setOf("old-a", "old-b"), db.todoDao().getAll().mapNotNull { it.externalId }.toSet())
    }

    @Test
    fun `课程作业请求并发上限不超过四个`() = runTest {
        db.tronCourseDao().insertAll((9002L..9007L).map { id ->
            tronCourse.copy(tronCourseId = id, name = "课程$id")
        })
        val api = ConcurrencyFakeApi()

        val result = repository(api).syncAssignments()

        assertTrue(result is TronResult.Success)
        assertTrue(api.maxInFlight.get() <= 4)
        assertTrue(api.maxInFlight.get() > 1)
    }

    @Test
    fun `接口课程ID可直接建立畅课课程引用`() = runTest {
        val repository = repository(FakeApi(Response.success(page(AssignmentDto(123L, "未匹配作业", "", 9001L, null, null)))))

        repository.syncAssignments()

        assertEquals(9001L, db.todoDao().getAll().single().courseId)
    }

    @Test
    fun `不同课程的同名作业按远端ID分别保留`() = runTest {
        db.tronCourseDao().insertAll(
            listOf(tronCourse.copy(tronCourseId = 9002L, name = "另一门课程")),
        )
        val api = FakeApi(
            responsesByCourse = mapOf(
                9001L to Response.success(page(AssignmentDto(301L, "第一次作业", null, 9001L, null, null))),
                9002L to Response.success(page(AssignmentDto(302L, "第一次作业", null, 9002L, null, null))),
            ),
        )

        val result = repository(api).syncAssignments()

        assertEquals(TronResult.Success(2), result)
        val todos = db.todoDao().getAll().filter { it.source == TodoSource.TRONCLASS.name }
        assertEquals(setOf("301", "302"), todos.mapNotNull { it.externalId }.toSet())
        assertEquals(setOf(9001L, 9002L), todos.mapNotNull { it.courseId }.toSet())
    }

    @Test
    fun `只对唯一active学期课程请求作业`() = runTest {
        val api = FakeApi(
            responsesByCourse = mapOf(
                9001L to Response.success(page(AssignmentDto(501L, "当前作业", null, 9001L, null, null))),
                9002L to Response.success(page(AssignmentDto(502L, "历史作业", null, 9002L, null, null))),
            ),
            coursesResponse = Response.success(courseResponse(9001L to 1L, 9002L to 2L)),
        )

        assertEquals(TronResult.Success(1), repository(api).syncAssignments())
        assertEquals(setOf(9001L), api.requestedCourseIds.toSet())
    }

    @Test
    fun `历史学期未来和无截止作业均不导入`() = runTest {
        val api = FakeApi(
            responsesByCourse = mapOf(
                9001L to Response.success(page(AssignmentDto(503L, "当前作业", null, 9001L, null, null))),
                9002L to Response.success(page(AssignmentDto(504L, "历史未来作业", null, 9002L, "2028-01-01T00:00:00Z", null))),
                9003L to Response.success(page(AssignmentDto(505L, "历史无截止作业", null, 9003L, null, null))),
            ),
            coursesResponse = Response.success(courseResponse(9001L to 1L, 9002L to 2L, 9003L to 2L)),
        )

        assertEquals(TronResult.Success(1), repository(api).syncAssignments())
        assertEquals(setOf(9001L), api.requestedCourseIds.toSet())
        assertEquals(setOf("503"), db.todoDao().getAll().mapNotNull { it.externalId }.toSet())
    }

    @Test
    fun `没有唯一active学期时fail safe且不请求作业`() = runTest {
        val old = TodoEntity(
            title = "缓存", description = "", courseId = 9002L,
            source = TodoSource.TRONCLASS.name, deadline = null,
            createdTime = 1L, updatedTime = 1L, externalId = "old",
        )
        db.todoDao().insert(old)
        val api = FakeApi(
            semesterResponse = Response.success(TronSemestersResponseDto(emptyList())),
            coursesResponse = Response.success(courseResponse(9001L to 1L)),
            defaultResponse = Response.success(page(AssignmentDto(506L, "不会请求", null, 9001L, null, null))),
        )

        assertEquals(TronClassError.CurrentSemesterUnavailable, (repository(api).syncAssignments() as TronResult.Error).error)
        assertTrue(api.requestedCourseIds.isEmpty())
        assertEquals(listOf(old.copy(id = 1L)), db.todoDao().getAll())
    }

    @Test
    fun `多个active学期时fail safe且不请求作业`() = runTest {
        val api = FakeApi(
            semesterResponse = Response.success(
                TronSemestersResponseDto(
                    listOf(
                        semester(id = 1L, active = true),
                        semester(id = 2L, active = true),
                    ),
                ),
            ),
            coursesResponse = Response.success(courseResponse(9001L to 1L, 9002L to 2L)),
        )

        assertEquals(TronClassError.CurrentSemesterAmbiguous, (repository(api).syncAssignments() as TronResult.Error).error)
        assertTrue(api.requestedCourseIds.isEmpty())
    }

    @Test
    fun `课程缺少semester id时不请求且不参与旧数据清理`() = runTest {
        val legacy = TodoEntity(
            title = "未知关联", description = "", courseId = 9002L,
            source = TodoSource.TRONCLASS.name, deadline = null,
            createdTime = 1L, updatedTime = 1L, externalId = "legacy",
        )
        db.todoDao().insert(legacy)
        val api = FakeApi(
            responsesByCourse = mapOf(
                9001L to Response.success(page(AssignmentDto(507L, "当前作业", null, 9001L, null, null))),
                9002L to Response.success(page(AssignmentDto(508L, "未知作业", null, 9002L, null, null))),
            ),
            coursesResponse = Response.success(courseResponse(9001L to 1L, 9002L to null)),
        )

        assertEquals(TronResult.Success(1), repository(api).syncAssignments())
        assertEquals(setOf(9001L), api.requestedCourseIds.toSet())
        assertTrue(db.todoDao().getAll().any { it.externalId == "legacy" })
    }

    @Test
    fun `成功同步后只清理已知非active学期畅课Todo`() = runTest {
        db.todoDao().insertAll(
            listOf(
                TodoEntity(title = "历史畅课", description = "", courseId = 9002L, source = TodoSource.TRONCLASS.name, deadline = null, createdTime = 1L, updatedTime = 1L, externalId = "old"),
                TodoEntity(title = "本地待办", description = "", courseId = 9002L, source = TodoSource.LOCAL.name, deadline = null, createdTime = 1L, updatedTime = 1L),
                TodoEntity(title = "未知畅课", description = "", courseId = 9999L, source = TodoSource.TRONCLASS.name, deadline = null, createdTime = 1L, updatedTime = 1L, externalId = "unknown"),
                TodoEntity(title = "无课程畅课", description = "", courseId = null, source = TodoSource.TRONCLASS.name, deadline = null, createdTime = 1L, updatedTime = 1L),
            ),
        )
        val api = FakeApi(
            coursesResponse = Response.success(courseResponse(9001L to 1L, 9002L to 2L)),
            defaultResponse = Response.success(page(AssignmentDto(509L, "当前作业", null, 9001L, null, null))),
        )

        assertEquals(TronResult.Success(1), repository(api).syncAssignments())
        assertFalse(db.todoDao().getAll().any { it.externalId == "old" })
        assertTrue(db.todoDao().getAll().any { it.source == TodoSource.LOCAL.name })
        assertTrue(db.todoDao().getAll().any { it.externalId == "unknown" })
        assertTrue(db.todoDao().getAll().any { it.courseId == null && it.source == TodoSource.TRONCLASS.name })
    }

    @Test
    fun `九门active学期课程只发出九次作业请求`() = runTest {
        val ids = (1001L..1041L).toList()
        val courses = ids.mapIndexed { index, id -> id to if (index < 9) 1L else 2L }
        val api = FakeApi(
            coursesResponse = Response.success(courseResponse(*courses.toTypedArray())),
            defaultResponse = Response.success(page(AssignmentDto(510L, "作业", null, 1001L, null, null))),
        )

        repository(api).syncAssignments()

        assertEquals(9, api.requestedCourseIds.size)
        assertTrue(api.requestedCourseIds.all { it in ids.take(9) })
    }

    private fun repository(
        api: TronClassApiService,
        pageSize: Int = 100,
        clock: () -> Long = { 9_000L },
    ) = AssignmentRepository(
        api = api,
        sessionProvider = sessionProvider,
        todoDao = db.todoDao(),
        parser = AssignmentParser(java.time.ZoneOffset.UTC),
        database = db,
        clock = clock,
        pageSize = pageSize,
    )

    private fun localCourse(name: String) = CourseEntity(
        id = 10L, semesterId = 1L, name = name, teacher = "教师", location = "",
        dayOfWeek = 1, startSection = 1, duration = 2, weeks = "1",
        source = "IMPORT", color = "#FFFFFF", note = "",
    )

    private fun page(vararg assignments: AssignmentDto, pages: Int? = 1) =
        AssignmentPageDto(assignments.toList(), pages, 1, 100)

    private fun courseResponse(vararg courses: Pair<Long, Long?>) =
        TronCoursesResponseDto(
            courses.map { (id, semesterId) ->
                TronCourseDto(
                    id,
                    "课程$id",
                    TronSemesterDto(null, null, null, id = semesterId, sort = semesterId?.toInt()),
                    emptyList(),
                    null,
                )
            },
        )

    private fun semester(id: Long, active: Boolean) =
        TronSemesterDto(null, null, null, id = id, sort = id.toInt(), isActive = active)

    private class FakeApi(
        private val responses: Map<Int, Response<AssignmentPageDto>> = emptyMap(),
        private val defaultResponse: Response<AssignmentPageDto>? = null,
        private val failure: IOException? = null,
        private val responsesByCourse: Map<Long, Response<AssignmentPageDto>> = emptyMap(),
        private val failureCourses: Set<Long> = emptySet(),
        private val semesterResponse: Response<TronSemestersResponseDto>? = null,
        private val coursesResponse: Response<TronCoursesResponseDto>? = null,
    ) : TronClassApiService {
        val requestedCourseIds = CopyOnWriteArrayList<Long>()

        constructor(response: Response<AssignmentPageDto>) : this(defaultResponse = response)

        override suspend fun getMySemesters(): Response<TronSemestersResponseDto> =
            semesterResponse ?: Response.success(
                TronSemestersResponseDto(
                    listOf(TronSemesterDto(null, null, null, id = 1L, sort = 1, isActive = true)),
                ),
            )

        override suspend fun getMyCourses(): Response<TronCoursesResponseDto> {
            coursesResponse?.let { return it }
            val ids = (setOf(9001L) + responsesByCourse.keys + failureCourses).sorted()
            return Response.success(
                TronCoursesResponseDto(
                    ids.map { id ->
                        TronCourseDto(
                            id,
                            "课程$id",
                            TronSemesterDto(null, null, null, id = 1L, sort = 1),
                            emptyList(),
                            null,
                        )
                    },
                ),
            )
        }

        override suspend fun getHomeworkActivities(
            courseId: Long,
            page: Int,
            pageSize: Int,
        ): Response<AssignmentPageDto> {
            requestedCourseIds += courseId
            failure?.let { throw it }
            if (courseId in failureCourses) throw IOException("synthetic course failure")
            responsesByCourse[courseId]?.let { return it }
            return responses[page] ?: defaultResponse ?: error("missing fake response")
        }

        override suspend fun getOfficialTodos(): Response<OfficialTodoResponseDto> =
            Response.success(OfficialTodoResponseDto(emptyList()))
    }

    private class ConcurrencyFakeApi : TronClassApiService {
        val maxInFlight = AtomicInteger(0)
        private val inFlight = AtomicInteger(0)

        override suspend fun getMySemesters(): Response<TronSemestersResponseDto> =
            Response.success(
                TronSemestersResponseDto(
                    listOf(TronSemesterDto(null, null, null, id = 1L, sort = 1, isActive = true)),
                ),
            )

        override suspend fun getMyCourses(): Response<TronCoursesResponseDto> =
            Response.success(
                TronCoursesResponseDto(
                    (9001L..9007L).map { id ->
                        TronCourseDto(
                            id,
                            "课程$id",
                            TronSemesterDto(null, null, null, id = 1L, sort = 1),
                            emptyList(),
                            null,
                        )
                    },
                ),
            )

        override suspend fun getHomeworkActivities(
            courseId: Long,
            page: Int,
            pageSize: Int,
        ): Response<AssignmentPageDto> {
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { old -> maxOf(old, current) }
            return try {
                delay(20)
                Response.success(
                    AssignmentPageDto(
                        homeworkActivities = listOf(AssignmentDto(courseId, "作业$courseId", null, courseId, null, null)),
                        pages = 1,
                        page = 1,
                        pageSize = pageSize,
                    ),
                )
            } finally {
                inFlight.decrementAndGet()
            }
        }

        override suspend fun getOfficialTodos(): Response<OfficialTodoResponseDto> =
            Response.success(OfficialTodoResponseDto(emptyList()))
    }
}
