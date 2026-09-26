package com.xmu.course.ui.todo

import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.data.todo.TodoAutoSyncCoordinator
import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.TodoRefreshResult as LegacyTodoRefreshResult
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        viewModel = TodoViewModel(repository)
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    @Test
    fun `初始空数据显示Empty`() = runTest {
        advanceUntilIdle()

        assertTrue(viewModel.state.value is TodoState.Empty)
    }

    @Test
    fun `Flow有待办时显示Success`() = runTest {
        repository.todos.value = listOf(todo())
        advanceUntilIdle()

        val state = viewModel.state.value as TodoState.Success
        assertEquals("测试待办", state.todos.single().title)
    }

    @Test
    fun `完成状态变化通过Repository更新`() = runTest {
        repository.todos.value = listOf(todo())
        advanceUntilIdle()

        viewModel.setCompleted(todo().id.value, true)
        advanceUntilIdle()

        assertTrue((viewModel.state.value as TodoState.Success).todos.single().completed)
        assertEquals(true, repository.lastCompleted)
    }

    @Test
    fun `feature model编辑通过现有update command写入`() = runTest {
        val model = TodoFeatureModel(
            id = TodoFeatureId(7L),
            title = "旧标题",
            description = "旧描述",
            courseReference = null,
            source = TodoFeatureSource.LOCAL,
            deadline = null,
            completed = false,
        )

        viewModel.updateTodo(model, "新标题", "新描述", 123L, null)
        advanceUntilIdle()

        assertEquals(
            EditTodoFeatureCommand(TodoFeatureId(7L), "新标题", "新描述", 123L, null),
            repository.lastUpdate,
        )
    }

    @Test
    fun `Repository失败显示安全Error`() = runTest {
        repository.failWrites = true
        advanceUntilIdle()

        viewModel.addTodo("新待办", "", null, null)
        advanceUntilIdle()

        assertEquals("待办保存失败，请重试", (viewModel.state.value as TodoState.Error).message)
    }

    @Test
    fun `主动刷新畅课待办委托协调器`() = runTest {
        var called = false
        val vm = TodoViewModel(repository, TodoRefreshReader {
            called = true
            TodoRefreshResult.Success(2)
        })
        advanceUntilIdle()

        vm.refreshFromTronClass()
        advanceUntilIdle()

        assertTrue(called)
        assertEquals(false, vm.isRefreshing.value)
    }

    @Test
    fun `手动刷新成功显示同步数量`() = runTest {
        val vm = TodoViewModel(repository, TodoRefreshReader { TodoRefreshResult.Success(4) })
        advanceUntilIdle()

        vm.refreshFromTronClass()
        advanceUntilIdle()

        assertEquals(TodoSyncSummary(importedCount = 4, automatic = false), vm.syncSummary.value)
    }

    @Test
    fun `自动刷新成功更新同步摘要但不显示错误`() = runTest {
        val vm = TodoViewModel(
            repository = repository,
            autoSyncCoordinator = TodoAutoSyncCoordinator(
                refreshCoordinator = TodoRefreshCoordinator { LegacyTodoRefreshResult.Success(4) },
                settings = object : TodoAutoSyncSettings {
                    override fun isEnabled(): Boolean = true
                    override fun setEnabled(enabled: Boolean) = Unit
                    override fun lastSuccessfulSyncAt(): Long? = null
                    override fun setLastSuccessfulSyncAt(timestamp: Long) = Unit
                },
            ),
            foregroundIntervalMillis = Long.MAX_VALUE,
        )

        vm.onForeground()
        runCurrent()

        assertEquals(TodoSyncSummary(importedCount = 4, automatic = true), vm.syncSummary.value)
        assertEquals(null, vm.autoSyncMessage.value)
        vm.onBackground()
    }

    @Test
    fun `多次前台事件只启动一次自动刷新循环`() = runTest {
        var calls = 0
        val vm = TodoViewModel(
            repository = repository,
            autoSyncCoordinator = TodoAutoSyncCoordinator(
                refreshCoordinator = TodoRefreshCoordinator {
                    calls++
                    LegacyTodoRefreshResult.Success(1)
                },
                settings = object : TodoAutoSyncSettings {
                    override fun isEnabled(): Boolean = true
                    override fun setEnabled(enabled: Boolean) = Unit
                    override fun lastSuccessfulSyncAt(): Long? = null
                    override fun setLastSuccessfulSyncAt(timestamp: Long) = Unit
                },
            ),
            foregroundIntervalMillis = Long.MAX_VALUE,
        )

        vm.onForeground()
        vm.onForeground()
        runCurrent()

        assertEquals(1, calls)
        vm.onBackground()
    }

    @Test
    fun `同步进行中忽略重复手动刷新`() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val vm = TodoViewModel(
            repository = repository,
            refreshReader = TodoRefreshReader {
                calls++
                started.complete(Unit)
                release.await()
                TodoRefreshResult.Success(1)
            },
        )

        vm.refreshFromTronClass()
        runCurrent()
        started.await()
        vm.refreshFromTronClass()
        runCurrent()

        assertEquals(1, calls)
        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `畅课刷新失败保留已有本地待办`() = runTest {
        repository.todos.value = listOf(todo())
        val vm = TodoViewModel(repository, TodoRefreshReader { TodoRefreshResult.Error })
        advanceUntilIdle()

        vm.refreshFromTronClass()
        advanceUntilIdle()

        val state = vm.state.value as TodoState.Error
        assertEquals("测试待办", state.todos.single().title)
    }

    private fun todo(completed: Boolean = false) = TodoFeatureModel(
        id = TodoFeatureId(1L),
        title = "测试待办",
        description = "",
        courseReference = null,
        source = TodoFeatureSource.LOCAL,
        deadline = null,
        completed = completed,
    )

    private class FakeRepository : TodoFeatureRepository {
        val todos = MutableStateFlow<List<TodoFeatureModel>>(emptyList())
        var failWrites = false
        var lastCompleted: Boolean? = null
        var lastUpdate: EditTodoFeatureCommand? = null

        override fun observeTodos(): Flow<List<TodoFeatureModel>> = todos

        override suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId {
            if (failWrites) error("synthetic")
            todos.value = listOf(todo())
            return TodoFeatureId(1L)
        }

        override suspend fun updateTodo(command: EditTodoFeatureCommand) {
            if (failWrites) error("synthetic")
            lastUpdate = command
            todos.value = listOf(todo().copy(
                title = command.title,
                description = command.description,
                deadline = command.deadline,
            ))
        }

        override suspend fun setCompleted(id: TodoFeatureId, completed: Boolean) {
            if (failWrites) error("synthetic")
            lastCompleted = completed
            todos.value = listOf(todo().copy(completed = completed))
        }

        override suspend fun deleteTodo(id: TodoFeatureId) {
            if (failWrites) error("synthetic")
            todos.value = emptyList()
        }

        override suspend fun getCourseOptions(): List<TodoCourseOptionModel> = emptyList()

        private fun todo() = TodoFeatureModel(
            id = TodoFeatureId(1L), title = "测试待办", description = "", courseReference = null,
            source = TodoFeatureSource.LOCAL, deadline = null, completed = false,
        )
    }
}
