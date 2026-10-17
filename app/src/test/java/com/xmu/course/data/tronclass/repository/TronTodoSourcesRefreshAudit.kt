package com.xmu.course.data.tronclass.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.todo.TodoDao
import com.xmu.course.data.todo.TodoRepository
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiService
import com.xmu.course.data.tronclass.api.TronCourseDto
import com.xmu.course.data.tronclass.api.TronCoursesResponseDto
import com.xmu.course.data.tronclass.api.TronSemesterDto
import com.xmu.course.data.tronclass.api.TronSemestersResponseDto
import com.xmu.course.data.tronclass.assignment.AssignmentDto
import com.xmu.course.data.tronclass.assignment.AssignmentPageDto
import com.xmu.course.data.tronclass.assignment.AssignmentRepository
import com.xmu.course.data.tronclass.assignment.TronTodoSyncMetadataEntity
import com.xmu.course.data.tronclass.auth.TronSession
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.tronclass.todo.OfficialTodoItemDto
import com.xmu.course.data.tronclass.todo.OfficialTodoRepository
import com.xmu.course.data.tronclass.todo.OfficialTodoResponseDto
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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

/** Synthetic API responses and an in-memory database only; no account or network access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TronTodoSourcesRefreshAudit {
    private lateinit var db: AppDatabase
    private val sessionProvider = SessionProvider { TronSession("session", "synthetic", 1L, 1L) }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        db.tronTodoSyncMetadataDao().insertIfAbsent(TronTodoSyncMetadataEntity(importBaselineAt = BASELINE))
        Unit
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun completePagedHomeworkSnapshotCompletesOnlyMissingFutureHomework() = runTest {
        db.todoDao().insertAll(
            listOf(
                cached("100"),
                cached("overdue", deadline = NOW - 1),
                cached("at-deadline", deadline = NOW),
                cached("no-deadline", deadline = null),
                cached("exam:100"),
                cached("other-course", courseId = 9999L),
                cached("local", source = TodoSource.LOCAL.name),
            ),
        )
        val api = FakeApi().apply {
            homework = { courseId, requestedPage, _ ->
                Response.success(homeworkPage(assignment(500L + requestedPage, courseId), page = requestedPage, pages = 2))
            }
        }

        assertEquals(TronResult.Success(2), assignments(api, pageSize = 1).syncAssignments())

        val saved = db.todoDao().getAll().associateBy { it.externalId }
        assertTrue("A complete successful snapshot must reconcile the missing future homework", saved.getValue("100").completed)
        listOf("overdue", "at-deadline", "no-deadline", "exam:100", "other-course", "local").forEach { id ->
            assertFalse("Reconciliation must not infer completion for $id", saved.getValue(id).completed)
        }
        assertTrue("Missing overdue homework remains overdue", TodoRepository.isOverdue(saved.getValue("overdue"), NOW, ZoneOffset.UTC))
    }

    @Test
    fun completeEmptyHomeworkSnapshotAlsoReconcilesPreviouslyImportedHomework() = runTest {
        db.todoDao().insert(cached("100"))

        assertEquals(TronResult.Success(0), assignments(FakeApi()).syncAssignments())

        assertTrue("An empty complete snapshot is still authoritative for the active course", db.todoDao().getAll().single().completed)
    }

    @Test
    fun failedSecondPageDoesNotWriteOrInferCompletionFromFirstPage() = runTest {
        db.todoDao().insert(cached("100"))
        val before = db.todoDao().getAll()
        val api = FakeApi().apply {
            homework = { courseId, requestedPage, _ ->
                if (requestedPage == 2) throw IOException("synthetic second page failure")
                Response.success(homeworkPage(assignment(501L, courseId), page = 1, pages = 2))
            }
        }

        assertEquals(TronResult.Error(TronClassError.NetworkError), assignments(api, pageSize = 1).syncAssignments())

        assertEquals("Partial snapshots must leave every cached field intact", before, db.todoDao().getAll())
        assertEquals(BASELINE, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
    }

    @Test
    fun repeatedFirstPageIsRejectedRatherThanAcceptedAsACompleteTwoPageSnapshot() = runTest {
        db.todoDao().insert(cached("100"))
        val before = db.todoDao().getAll()
        val api = FakeApi().apply {
            // Both responses explicitly identify themselves as page 1 of 2.
            homework = { courseId, _, _ ->
                Response.success(homeworkPage(assignment(501L, courseId), page = 1, pages = 2))
            }
        }

        val result = assignments(api, pageSize = 1).syncAssignments()

        assertTrue("A response for page 1 cannot prove that requested page 2 was received: $result", result is TronResult.Error)
        assertEquals("An incomplete snapshot must not mutate cached todos", before, db.todoDao().getAll())
    }

    @Test
    fun incompleteOrInconsistentPaginationPreservesWholeCache() = runTest {
        db.todoDao().insert(cached("100"))
        val before = db.todoDao().getAll()
        val first = homeworkPage(assignment(501L), page = 1, pages = 2)
        val malformedSecondPages = listOf(
            homeworkPage(assignment(502L), page = null, pages = 2),
            homeworkPage(page = 2, pages = 2),
            homeworkPage(assignment(502L), page = 2, pages = null),
            homeworkPage(assignment(502L), page = 2, pages = 3),
            homeworkPage(assignment(502L), page = 3, pages = 2),
        )
        malformedSecondPages.forEachIndexed { case, second ->
            val api = FakeApi().apply {
                homework = { _, page, _ -> Response.success(if (page == 1) first else second) }
            }

            assertTrue("Malformed page case $case must fail", assignments(api, pageSize = 1).syncAssignments() is TronResult.Error)
            assertEquals("Malformed page case $case must preserve cached values and completion", before, db.todoDao().getAll())
            assertEquals("Malformed page case $case must preserve the baseline", BASELINE, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
        }
    }

    @Test
    fun responsesWithoutPaginationMetadataStillUseReportedPageSizeToReadEveryPage() = runTest {
        val requestedPages = mutableListOf<Int>()
        val api = FakeApi().apply {
            homework = { _, page, _ ->
                requestedPages += page
                Response.success(
                    if (page == 1) homeworkPage(assignment(501L), page = null, pages = null)
                    else homeworkPage(page = null, pages = null),
                )
            }
        }

        assertEquals(TronResult.Success(1), assignments(api, pageSize = 100).syncAssignments())

        assertEquals("The server reports page size 1, so a full first page is not the end", listOf(1, 2), requestedPages)
        assertEquals("501", db.todoDao().getAll().single().externalId)
    }

    @Test
    fun existingHomeworkStillUpdatesWhenDeadlineIsMovedBeforeImportBaseline() = runTest {
        db.todoDao().insert(cached("100"))
        val api = FakeApi().apply {
            homework = { courseId, _, _ ->
                Response.success(homeworkPage(assignment(100L, courseId, title = "Changed", deadline = BASELINE - 1)))
            }
        }

        assertTrue(assignments(api).syncAssignments() is TronResult.Success)

        val saved = db.todoDao().getAll().single()
        assertEquals("The first-import cutoff must not freeze an existing todo's deadline", BASELINE - 1, saved.deadline)
        assertEquals("Changed", saved.title)
        assertTrue("The refreshed deadline is overdue now", TodoRepository.isOverdue(saved, NOW, ZoneOffset.UTC))
    }

    @Test
    fun existingExamStillUpdatesWhenDeadlineIsMovedBeforeImportBaseline() = runTest {
        db.todoDao().insert(cached("exam:100"))
        val api = FakeApi().apply {
            exams = { Response.success(OfficialTodoResponseDto(listOf(exam(100L, title = "Changed", deadline = BASELINE - 1)))) }
        }

        assertTrue(official(api).syncExamTodos() is TronResult.Success)

        val saved = db.todoDao().getAll().single()
        assertEquals("The first-import cutoff must not freeze an existing exam's deadline", BASELINE - 1, saved.deadline)
        assertEquals("Changed", saved.title)
        assertTrue("The refreshed exam deadline is overdue now", TodoRepository.isOverdue(saved, NOW, ZoneOffset.UTC))
    }

    @Test
    fun existingPastDeadlineUpdatesRetainLocalStateWhileNewPastItemsStayExcluded() = runTest {
        for (isExam in listOf(false, true)) {
            db.todoDao().deleteAll()
            val externalId = if (isExam) "exam:100" else "100"
            val old = cached(externalId).copy(completed = true, createdTime = 123L)
            val id = db.todoDao().insert(old)
            val api = FakeApi().apply {
                homework = { courseId, _, _ ->
                    Response.success(homeworkPage(
                        assignment(100L, courseId, title = "Changed", deadline = BASELINE - 1),
                        assignment(101L, courseId, deadline = BASELINE - 2),
                    ))
                }
                exams = { Response.success(OfficialTodoResponseDto(listOf(
                    exam(100L, title = "Changed", deadline = BASELINE - 1),
                    exam(101L, deadline = BASELINE - 2),
                ))) }
            }

            val result = if (isExam) official(api).syncExamTodos() else assignments(api).syncAssignments()

            assertEquals("Only the existing item is updated ($externalId)", TronResult.Success(1), result)
            assertEquals(old.copy(id = id, title = "Changed", deadline = BASELINE - 1, updatedTime = NOW), db.todoDao().getAll().single())
            assertEquals(BASELINE, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
        }
    }

    @Test
    fun creatingInitialBaselineDoesNotEraseLocalStateOfAnExistingItemBeingUpdated() = runTest {
        for (isExam in listOf(false, true)) {
            db.todoDao().deleteAll()
            db.openHelper.writableDatabase.execSQL("DELETE FROM tron_todo_sync_metadata")
            val externalId = if (isExam) "exam:100" else "100"
            val old = cached(externalId, deadline = BASELINE - 1).copy(completed = true, createdTime = 123L)
            val id = db.todoDao().insert(old)
            val api = FakeApi().apply {
                homework = { courseId, _, _ -> Response.success(homeworkPage(
                    assignment(100L, courseId, title = "Changed", deadline = BASELINE - 1),
                    assignment(101L, courseId, deadline = BASELINE - 2),
                )) }
                exams = { Response.success(OfficialTodoResponseDto(listOf(
                    exam(100L, title = "Changed", deadline = BASELINE - 1),
                    exam(101L, deadline = BASELINE - 2),
                ))) }
            }

            val result = if (isExam) official(api).syncExamTodos() else assignments(api).syncAssignments()

            assertEquals(TronResult.Success(1), result)
            assertEquals("Initial cleanup must preserve the ID, completed state and creation time of $externalId", old.copy(id = id, title = "Changed", updatedTime = NOW), db.todoDao().getAll().single())
            assertEquals(NOW, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
        }
    }

    @Test
    fun anExplicitEmptyZeroPageSnapshotRetainsItsPreviousValidSemantics() = runTest {
        db.todoDao().insert(cached("100"))
        val api = FakeApi().apply {
            homework = { _, _, _ -> Response.success(AssignmentPageDto(emptyList(), 0, 1, 0)) }
        }

        assertEquals(TronResult.Success(0), assignments(api).syncAssignments())
        assertTrue(db.todoDao().getAll().single().completed)
    }

    @Test
    fun initialBaselineFromTheOtherEndpointDoesNotEraseAnExistingItemBeforeItsUpdate() = runTest {
        for (isExam in listOf(false, true)) {
            db.todoDao().deleteAll()
            db.openHelper.writableDatabase.execSQL("DELETE FROM tron_todo_sync_metadata")
            val externalId = if (isExam) "exam:100" else "100"
            val old = cached(externalId, deadline = BASELINE - 1).copy(completed = true, createdTime = 123L)
            val id = db.todoDao().insert(old)
            val api = FakeApi().apply {
                homework = { courseId, _, _ -> Response.success(homeworkPage(
                    assignment(100L, courseId, title = "Changed", deadline = BASELINE - 2),
                )) }
                exams = { Response.success(OfficialTodoResponseDto(listOf(
                    exam(100L, title = "Changed", deadline = BASELINE - 2),
                ))) }
            }

            if (isExam) assignments(api).syncAssignments() else official(api).syncExamTodos()
            val result = if (isExam) official(api).syncExamTodos() else assignments(api).syncAssignments()

            assertEquals(TronResult.Success(1), result)
            assertEquals(old.copy(id = id, title = "Changed", deadline = BASELINE - 2, updatedTime = NOW),
                db.todoDao().getAll().single())
        }
    }

    @Test
    fun networkCancellationPropagatesAtEverySyncStageWithoutWritingCache() = runTest {
        for (isExam in listOf(false, true)) {
            for (stage in listOf("semesters", "courses", "items")) {
                db.todoDao().deleteAll()
                db.todoDao().insert(cached(if (isExam) "exam:100" else "100"))
                val before = db.todoDao().getAll()
                val cancelled = CancellationException("synthetic $isExam $stage cancellation")
                val api = FakeApi().apply {
                    when (stage) {
                        "semesters" -> semesters = { throw cancelled }
                        "courses" -> courseResponse = { throw cancelled }
                        else -> if (isExam) exams = { throw cancelled }
                        else homework = { _, _, _ -> throw cancelled }
                    }
                }

                assertCancellationPropagates(cancelled) {
                    if (isExam) official(api).syncExamTodos() else assignments(api).syncAssignments()
                }

                assertEquals("Cancellation at $isExam/$stage must preserve all cached fields", before, db.todoDao().getAll())
                assertEquals(BASELINE, db.tronTodoSyncMetadataDao().get()!!.importBaselineAt)
            }
        }
    }

    @Test
    fun sessionProviderCancellationIsNotConvertedIntoMissingSession() = runTest {
        for (isExam in listOf(false, true)) {
            val cancelled = CancellationException("synthetic session cancellation")
            val provider = SessionProvider { throw cancelled }
            val api = FakeApi().apply { semesters = { error("Session cancellation must stop before networking") } }

            assertCancellationPropagates(cancelled) {
                if (isExam) official(api, session = provider).syncExamTodos()
                else assignments(api, session = provider).syncAssignments()
            }
        }
    }

    @Test
    fun transactionCancellationRollsBackBaselineCleanupAndWritesAndReleasesMutex() = runTest {
        for (isExam in listOf(false, true)) {
            db.todoDao().deleteAll()
            db.openHelper.writableDatabase.execSQL("DELETE FROM tron_todo_sync_metadata")
            db.todoDao().insert(cached("legacy", deadline = BASELINE - 1).copy(completed = true, createdTime = 123L))
            val dao = db.todoDao()
            val before = dao.getAll()
            val cancelled = CancellationException("synthetic transaction cancellation")
            var cancelNextWrite = true
            val cancellingDao = object : TodoDao by dao {
                override suspend fun upsertExternalTodos(todos: List<TodoEntity>) {
                    dao.upsertExternalTodos(todos)
                    if (cancelNextWrite) {
                        cancelNextWrite = false
                        throw cancelled
                    }
                }
            }
            val api = FakeApi().apply {
                homework = { courseId, _, _ -> Response.success(homeworkPage(assignment(501L, courseId))) }
                exams = { Response.success(OfficialTodoResponseDto(listOf(exam(501L)))) }
            }
            val homeworkRepository = assignments(api, todoDao = cancellingDao)
            val examRepository = official(api, todoDao = cancellingDao)

            assertCancellationPropagates(cancelled) {
                if (isExam) examRepository.syncExamTodos() else homeworkRepository.syncAssignments()
            }

            assertEquals("A cancelled transaction must restore deleted history and remove new rows", before, dao.getAll())
            assertEquals("A cancelled transaction must not persist the first baseline", null, db.tronTodoSyncMetadataDao().get())
            val retry = if (isExam) examRepository.syncExamTodos() else homeworkRepository.syncAssignments()
            assertEquals("The same repository must release its mutex after cancellation", TronResult.Success(1), retry)
        }
    }

    @Test
    fun delayedOldSemesterSnapshotCannotDeleteNewSemesterTodosAndResurrectOldOnes() = runTest {
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeApi().apply {
            courses = listOf(course(9001L, 10L), course(9002L, 11L))
            homework = { courseId, _, _ ->
                if (courseId == 9001L) {
                    captured.complete(Unit)
                    release.await()
                }
                Response.success(homeworkPage(assignment(courseId, courseId)))
            }
        }
        val repository = assignments(api)
        val older = async { repository.syncAssignments() }
        var newer: kotlinx.coroutines.Deferred<TronResult<Int>>? = null
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000L) { captured.await() } }
            api.activeSemesterId = 11L
            val request = async { repository.syncAssignments() }
            newer = request
            // Also permits a future implementation that serializes refreshes instead of
            // accepting out-of-order writes; releasing the old response must unblock it.
            withContext(Dispatchers.Default) { withTimeoutOrNull(3_000L) { request.await() } }?.let {
                assertTrue(it is TronResult.Success)
                assertEquals("New semester committed before the stale request is released", setOf("9002"), db.todoDao().getAll().map { row -> row.externalId }.toSet())
            }
        } finally {
            release.complete(Unit)
        }
        older.await()
        assertTrue(newer!!.await() is TronResult.Success)

        assertEquals("The earlier semester snapshot must not delete the newer semester's data", setOf("9002"), db.todoDao().getAll().map { it.externalId }.toSet())
    }

    @Test
    fun delayedOlderExamResponseCannotOverwriteNewerTitleAndDeadline() = runTest {
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val api = FakeApi().apply {
            exams = {
                val isOld = calls.incrementAndGet() == 1
                if (isOld) {
                    captured.complete(Unit)
                    release.await()
                }
                Response.success(
                    OfficialTodoResponseDto(listOf(exam(100L, title = if (isOld) "Old" else "New", deadline = if (isOld) 40_000L else 50_000L))),
                )
            }
        }
        val repository = official(api)
        val older = async { repository.syncExamTodos() }
        var newer: kotlinx.coroutines.Deferred<TronResult<Int>>? = null
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000L) { captured.await() } }
            val request = async { repository.syncExamTodos() }
            newer = request
            withContext(Dispatchers.Default) { withTimeoutOrNull(3_000L) { request.await() } }?.let { assertTrue(it is TronResult.Success) }
        } finally {
            release.complete(Unit)
        }
        older.await()
        assertTrue(newer!!.await() is TronResult.Success)

        val saved = db.todoDao().getAll().single()
        assertEquals("A stale response must not restore the old exam title", "New", saved.title)
        assertEquals("A stale response must not restore the old exam deadline", 50_000L, saved.deadline)
    }

    @Test
    fun combinedRefreshPreservesSuccessfulHomeworkWhenExamSourceFails() = runTest {
        db.todoDao().insert(cached("exam:100"))
        val api = FakeApi().apply {
            homework = { courseId, _, _ -> Response.success(homeworkPage(assignment(501L, courseId))) }
            exams = { throw IOException("synthetic exam failure") }
        }
        val repository = TronClassRepository(
            api = api,
            sessionProvider = sessionProvider,
            courseDao = db.tronCourseDao(),
            assignmentRepository = assignments(api),
            officialTodoRepository = official(api),
            clock = { NOW },
        )

        assertEquals(TronResult.Error(TronClassError.NetworkError), repository.syncTodoSources())

        assertEquals("An overall error does not imply rollback of the successful source", setOf("501", "exam:100"), db.todoDao().getAll().map { it.externalId }.toSet())
    }

    @Test
    fun courseSyncLeavesTodosUntouchedButExplicitTodoSourceSyncUpdatesExistingRow() = runTest {
        db.todoDao().insert(cached("100"))
        val before = db.todoDao().getAll()
        val api = FakeApi().apply {
            homework = { courseId, _, _ -> Response.success(homeworkPage(assignment(100L, courseId, title = "Changed"))) }
        }
        val repository = TronClassRepository(
            api = api,
            sessionProvider = sessionProvider,
            courseDao = db.tronCourseDao(),
            assignmentRepository = assignments(api),
            officialTodoRepository = official(api),
            clock = { NOW },
        )

        assertTrue(repository.syncCourses() is TronResult.Success)
        assertEquals("Course refresh alone has no todo side effect", before, db.todoDao().getAll())
        assertEquals(TronResult.Success(TronTodoSyncSummary(1, 0)), repository.syncTodoSources())
        assertEquals("The explicit todo path must update the same local row", "Changed", db.todoDao().getAll().single().title)
        assertEquals(before.single().id, db.todoDao().getAll().single().id)
    }

    @Test
    fun emptyOfficialExamListDoesNotInferCompletionOrDeletionWithoutAuthority() = runTest {
        db.todoDao().insert(cached("exam:100"))
        val before = db.todoDao().getAll()

        assertEquals(TronResult.Success(0), official(FakeApi()).syncExamTodos())

        // Existing explicit policy: the official aggregation list has no complete-snapshot guarantee.
        assertEquals("Do not silently extend homework reconciliation to exam items", before, db.todoDao().getAll())
    }

    private suspend fun assertCancellationPropagates(expected: CancellationException, action: suspend () -> Any?) {
        val thrown = try {
            action()
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }
        // Coroutine stack-trace recovery may copy the exception across withContext boundaries.
        // Require the original cancellation in its cause chain, never a returned sync error.
        assertTrue("The original cancellation must propagate instead of becoming a sync error",
            generateSequence<Throwable>(thrown) { it.cause }.any { it === expected })
    }

    private fun assignments(
        api: TronClassApiService,
        pageSize: Int = 100,
        todoDao: TodoDao = db.todoDao(),
        session: SessionProvider = sessionProvider,
    ) = AssignmentRepository(
        api = api, sessionProvider = session, todoDao = todoDao, database = db,
        clock = { NOW }, pageSize = pageSize,
    )

    private fun official(
        api: TronClassApiService,
        todoDao: TodoDao = db.todoDao(),
        session: SessionProvider = sessionProvider,
    ) = OfficialTodoRepository(
        api = api, sessionProvider = session, todoDao = todoDao, database = db,
        clock = { NOW },
    )

    private fun cached(
        externalId: String,
        deadline: Long? = 40_000L,
        courseId: Long = 9001L,
        source: String = TodoSource.TRONCLASS.name,
    ) = TodoEntity(
        title = "Cached $externalId", description = "", courseId = courseId, source = source,
        deadline = deadline, createdTime = BASELINE, updatedTime = BASELINE, externalId = externalId,
    )

    private class FakeApi : TronClassApiService {
        @Volatile var activeSemesterId = 10L
        var courses = listOf(course(9001L, 10L))
        var homework: suspend (Long, Int, Int) -> Response<AssignmentPageDto> = { _, _, _ ->
            Response.success(homeworkPage())
        }
        var exams: suspend () -> Response<OfficialTodoResponseDto> = {
            Response.success(OfficialTodoResponseDto(emptyList()))
        }
        var semesters: suspend () -> Response<TronSemestersResponseDto> = {
            Response.success(TronSemestersResponseDto(listOf(TronSemesterDto(null, null, null, id = activeSemesterId, isActive = true))))
        }
        var courseResponse: suspend () -> Response<TronCoursesResponseDto> = {
            Response.success(TronCoursesResponseDto(courses))
        }

        override suspend fun getMySemesters() = semesters()

        override suspend fun getMyCourses() = courseResponse()

        override suspend fun getHomeworkActivities(courseId: Long, page: Int, pageSize: Int) =
            homework(courseId, page, pageSize)

        override suspend fun getOfficialTodos() = exams()
    }

    private companion object {
        const val BASELINE = 1_000L
        const val NOW = 10_000L

        fun course(id: Long, semesterId: Long) = TronCourseDto(
            id, "Synthetic course $id", TronSemesterDto(null, null, null, id = semesterId), emptyList(), null,
        )

        fun assignment(id: Long, courseId: Long = 9001L, title: String = "Homework $id", deadline: Long = 40_000L) =
            AssignmentDto(id, title, "", courseId, Instant.ofEpochMilli(deadline).toString(), null)

        fun homeworkPage(vararg items: AssignmentDto, page: Int? = 1, pages: Int? = 1) =
            AssignmentPageDto(items.toList(), pages, page, 1)

        fun exam(id: Long, title: String = "Exam $id", deadline: Long = 40_000L) = OfficialTodoItemDto(
            id, title, 9001L, "Synthetic course", "SYN", Instant.ofEpochMilli(deadline).toString(), "exam", true, false,
        )
    }
}
