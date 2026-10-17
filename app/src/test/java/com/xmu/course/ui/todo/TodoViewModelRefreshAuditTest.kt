package com.xmu.course.ui.todo

import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.data.todo.TodoAutoSyncCoordinator
import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.TodoRefreshResult as LegacyTodoRefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Synthetic-only audit of refresh state owned by the Todo feature UI. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoViewModelRefreshAuditTest {
    private val dispatcher = StandardTestDispatcher()
    private val foregroundViewModels = mutableListOf<TodoViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        foregroundViewModels.forEach(TodoViewModel::onBackground)
        Dispatchers.resetMain()
    }

    @Test
    fun `manual refresh loads names for courses imported after app startup`() = runTest {
        val repository = AuditRepository()
        val importedCourse = course("新同步的课程")
        val vm = TodoViewModel(repository, TodoRefreshReader {
            repository.options = listOf(importedCourse)
            repository.todos.value = listOf(todo())
            TodoRefreshResult.Success(1)
        })
        runCurrent()
        assertTrue(vm.state.value is TodoState.Empty)

        vm.refreshFromTronClass()
        runCurrent()

        val state = vm.state.value as TodoState.Success
        assertEquals(listOf(importedCourse), state.courseOptions)
        assertEquals(importedCourse.reference, state.todos.single().courseReference)
    }

    @Test
    fun `external sync publishes imported course and completed state without view model refresh`() = runTest {
        val repository = AuditRepository()
        val vm = TodoViewModel(repository)
        runCurrent()
        assertTrue(vm.state.value is TodoState.Empty)

        // TronClass's own screen writes these rows, then navigates back to Todo.
        val importedCourse = course("从畅课页面导入的课程")
        repository.options = listOf(importedCourse)
        repository.todos.value = listOf(todo().copy(completed = true))
        runCurrent()

        val state = vm.state.value as TodoState.Success
        assertEquals(listOf(importedCourse), state.courseOptions)
        assertEquals(importedCourse.reference, state.todos.single().courseReference)
        assertTrue(state.todos.single().completed)
    }

    @Test
    fun `automatic refresh replaces cached course name after provider renames course`() = runTest {
        val repository = AuditRepository().apply {
            options = listOf(course("旧课程名"))
            todos.value = listOf(todo())
        }
        val renamedCourse = course("更新后的课程名")
        val vm = TodoViewModel(
            repository = repository,
            autoSyncCoordinator = coordinator {
                repository.options = listOf(renamedCourse)
                repository.todos.value = listOf(todo().copy(title = "作业标题已更新"))
                LegacyTodoRefreshResult.Success(1)
            },
            foregroundIntervalMillis = Long.MAX_VALUE,
        )
        foregroundViewModels += vm
        runCurrent()
        assertEquals("旧课程名", (vm.state.value as TodoState.Success).courseOptions.single().name)

        vm.onForeground()
        runCurrent()

        val state = vm.state.value as TodoState.Success
        assertEquals("作业标题已更新", state.todos.single().title)
        assertEquals(listOf(renamedCourse), state.courseOptions)
        vm.onBackground()
        runCurrent()
    }

    @Test
    fun `successful retry clears prior refresh error even when cached rows do not change`() = runTest {
        val repository = AuditRepository().apply { todos.value = listOf(todo()) }
        var result: TodoRefreshResult = TodoRefreshResult.SessionExpired
        val vm = TodoViewModel(repository, TodoRefreshReader { result })
        runCurrent()

        vm.refreshFromTronClass()
        runCurrent()
        assertTrue(vm.state.value is TodoState.Error)

        // User logs in again. An unchanged successful response need not emit a new Room list.
        result = TodoRefreshResult.Success(0)
        vm.refreshFromTronClass()
        runCurrent()

        assertEquals(TodoSyncSummary(0, automatic = false), vm.syncSummary.value)
        assertFalse(vm.isRefreshing.value)
        assertTrue("Successful retry must remove the obsolete login error", vm.state.value is TodoState.Success)
        assertEquals(listOf(todo()), (vm.state.value as TodoState.Success).todos)
    }

    @Test
    fun `successful retry restores empty content after refresh failure with no rows`() = runTest {
        val repository = AuditRepository()
        var result: TodoRefreshResult = TodoRefreshResult.Error
        val vm = TodoViewModel(repository, TodoRefreshReader { result })
        runCurrent()
        vm.refreshFromTronClass()
        runCurrent()
        assertTrue(vm.state.value is TodoState.Error)

        result = TodoRefreshResult.Success(0)
        vm.refreshFromTronClass()
        runCurrent()

        assertTrue(vm.state.value is TodoState.Empty)
        assertFalse(vm.isRefreshing.value)
        assertNull(vm.autoSyncMessage.value)
    }

    @Test
    fun `manual and automatic network success preserve actual local todo read failure`() = runTest {
        val repository = AuditRepository().apply { todos.value = listOf(todo()) }
        var result: TodoRefreshResult = TodoRefreshResult.Error
        val vm = TodoViewModel(
            repository = repository,
            refreshReader = TodoRefreshReader { result },
            autoSyncCoordinator = coordinator { LegacyTodoRefreshResult.Success(0) },
            foregroundIntervalMillis = Long.MAX_VALUE,
        )
        foregroundViewModels += vm
        runCurrent()
        repository.failTodoReads = true
        repository.todos.value = listOf(todo().copy(title = "触发合成读取失败"))
        runCurrent()
        assertEquals("待办数据读取失败，请稍后重试", (vm.state.value as TodoState.Error).message)

        // Even a failed manual request must not replace the primary local-read failure.
        vm.refreshFromTronClass()
        runCurrent()
        result = TodoRefreshResult.Success(0)
        vm.refreshFromTronClass()
        runCurrent()
        vm.onForeground()
        runCurrent()

        val state = vm.state.value as TodoState.Error
        assertEquals("待办数据读取失败，请稍后重试", state.message)
        assertEquals(listOf(todo()), state.todos)
        assertEquals(TodoSyncSummary(0, automatic = true), vm.syncSummary.value)
        vm.onBackground()
        runCurrent()
    }

    @Test
    fun `course directory failure keeps cached names and does not stop subsequent todo emissions`() = runTest {
        val oldCourse = course("缓存课程名")
        val repository = AuditRepository().apply {
            options = listOf(oldCourse)
            todos.value = listOf(todo())
        }
        val vm = TodoViewModel(repository)
        runCurrent()
        repository.failCourseReads = true
        repository.todos.value = listOf(todo().copy(completed = true))
        runCurrent()

        val whileDirectoryUnavailable = vm.state.value as TodoState.Success
        assertTrue(whileDirectoryUnavailable.todos.single().completed)
        assertEquals(listOf(oldCourse), whileDirectoryUnavailable.courseOptions)

        val newCourse = course("恢复后的课程名")
        repository.failCourseReads = false
        repository.options = listOf(newCourse)
        repository.todos.value = listOf(todo().copy(title = "下一次本地更新", completed = true))
        runCurrent()

        val recovered = vm.state.value as TodoState.Success
        assertEquals("下一次本地更新", recovered.todos.single().title)
        assertEquals(listOf(newCourse), recovered.courseOptions)
    }

    @Test
    fun `cancelled manual refresh stops spinner without publishing a failure`() = runTest {
        val repository = AuditRepository().apply { todos.value = listOf(todo()) }
        val vm = TodoViewModel(repository, TodoRefreshReader {
            throw CancellationException("synthetic cancellation")
        })
        runCurrent()

        vm.refreshFromTronClass()
        runCurrent()

        assertFalse(vm.isRefreshing.value)
        assertTrue(vm.state.value is TodoState.Success)
        assertNull(vm.syncSummary.value)
        assertNull(vm.autoSyncMessage.value)
    }

    @Test
    fun `cancelled automatic refresh propagates cancellation instead of keeping hourly loop alive`() = runTest {
        val repository = AuditRepository().apply { todos.value = listOf(todo()) }
        var calls = 0
        val vm = TodoViewModel(
            repository = repository,
            autoSyncCoordinator = coordinator {
                calls++
                throw CancellationException("synthetic cancellation")
            },
            foregroundIntervalMillis = 60_000L,
        )
        foregroundViewModels += vm
        runCurrent()
        vm.onForeground()
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(1, calls)
        assertTrue(vm.state.value is TodoState.Success)
        assertNull(vm.syncSummary.value)
        assertNull(vm.autoSyncMessage.value)
        vm.onBackground()
        runCurrent()
    }

    @Test
    fun `manual recovery clears automatic refresh failure message`() = runTest {
        val repository = AuditRepository().apply { todos.value = listOf(todo()) }
        val vm = TodoViewModel(
            repository = repository,
            refreshReader = TodoRefreshReader { TodoRefreshResult.Success(0) },
            autoSyncCoordinator = coordinator { LegacyTodoRefreshResult.Error },
            foregroundIntervalMillis = Long.MAX_VALUE,
        )
        foregroundViewModels += vm
        runCurrent()
        vm.onForeground()
        runCurrent()
        assertEquals("畅课待办自动刷新失败，已保留本地缓存", vm.autoSyncMessage.value)

        vm.refreshFromTronClass()
        runCurrent()

        assertEquals(TodoSyncSummary(0, automatic = false), vm.syncSummary.value)
        assertNull("Successful manual recovery must clear the stale automatic error", vm.autoSyncMessage.value)
        vm.onBackground()
        runCurrent()
    }

    @Test
    fun `background cancels automatic request and queued manual refresh finishes without overlap`() = runTest {
        val repository = AuditRepository().apply { todos.value = listOf(todo()) }
        val holdAutomaticRequest = CompletableDeferred<Unit>()
        var automaticCalls = 0
        var manualCalls = 0
        var activeAutomaticRequests = 0
        var overlappingRequests = false
        val vm = TodoViewModel(
            repository = repository,
            refreshReader = TodoRefreshReader {
                overlappingRequests = activeAutomaticRequests != 0
                manualCalls++
                TodoRefreshResult.Success(0)
            },
            autoSyncCoordinator = coordinator {
                automaticCalls++
                activeAutomaticRequests++
                try {
                    holdAutomaticRequest.await()
                    LegacyTodoRefreshResult.Success(0)
                } finally {
                    activeAutomaticRequests--
                }
            },
            foregroundIntervalMillis = Long.MAX_VALUE,
        )
        foregroundViewModels += vm
        runCurrent()
        vm.onForeground()
        runCurrent()
        assertEquals(1, automaticCalls)

        vm.refreshFromTronClass()
        runCurrent()
        assertTrue(vm.isRefreshing.value)
        assertEquals(0, manualCalls)

        vm.onBackground()
        runCurrent()

        assertEquals(1, manualCalls)
        assertFalse(overlappingRequests)
        assertFalse(vm.isRefreshing.value)
        assertNull(vm.autoSyncMessage.value)
        assertTrue(vm.state.value is TodoState.Success)
        assertEquals(TodoSyncSummary(0, automatic = false), vm.syncSummary.value)
    }

    private fun coordinator(refresh: suspend () -> LegacyTodoRefreshResult) = TodoAutoSyncCoordinator(
        refreshCoordinator = TodoRefreshCoordinator { refresh() },
        settings = object : TodoAutoSyncSettings {
            private var lastSuccessful: Long? = null
            override fun isEnabled(): Boolean = true
            override fun setEnabled(enabled: Boolean) = Unit
            override fun lastSuccessfulSyncAt(): Long? = lastSuccessful
            override fun setLastSuccessfulSyncAt(timestamp: Long) { lastSuccessful = timestamp }
        },
    )

    private class AuditRepository : TodoFeatureRepository {
        val todos = MutableStateFlow<List<TodoFeatureModel>>(emptyList())
        var options = emptyList<TodoCourseOptionModel>()
        var failTodoReads = false
        var failCourseReads = false

        override fun observeTodos(): Flow<List<TodoFeatureModel>> = todos.map {
            if (failTodoReads) error("Synthetic Todo read failure")
            it
        }
        override suspend fun getCourseOptions(): List<TodoCourseOptionModel> {
            if (failCourseReads) error("Synthetic course directory read failure")
            return options
        }
        override suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId = error("Not used by audit")
        override suspend fun updateTodo(command: EditTodoFeatureCommand): Unit = error("Not used by audit")
        override suspend fun setCompleted(id: TodoFeatureId, completed: Boolean): Unit = error("Not used by audit")
        override suspend fun deleteTodo(id: TodoFeatureId): Unit = error("Not used by audit")
    }

    private fun course(name: String) = TodoCourseOptionModel(
        reference = TodoCourseReference.External(101),
        name = name,
        teacher = "合成教师",
    )

    private fun todo() = TodoFeatureModel(
        id = TodoFeatureId(1),
        title = "合成作业",
        description = "",
        courseReference = TodoCourseReference.External(101),
        source = TodoFeatureSource.EXTERNAL,
        deadline = null,
        completed = false,
    )
}
