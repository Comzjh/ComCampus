package com.xmu.course.data.todo

import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.di.TodoDependencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TodoDependenciesTest {
    @Test
    fun `dependency graph retains injected repository and coordinator instances`() {
        val repository = FakeRepository()
        val refresh = FakeRefresh()
        val settings = FakeSettings()
        val autoSync = TodoAutoSyncCoordinator(
            refreshCoordinator = refresh,
            settings = settings,
            now = { 10_000L },
            intervalMillis = 1L,
        )

        val dependencies = TodoDependencies(repository, refresh, autoSync)

        assertSame(repository, dependencies.repository)
        assertSame(refresh, dependencies.refreshCoordinator)
        assertSame(autoSync, dependencies.autoSyncCoordinator)
    }

    @Test
    fun `auto sync in the graph reuses the injected refresh coordinator`() = runTest {
        val refresh = FakeRefresh()
        val dependencies = TodoDependencies(
            repository = FakeRepository(),
            refreshCoordinator = refresh,
            autoSyncCoordinator = TodoAutoSyncCoordinator(
                refreshCoordinator = refresh,
                settings = FakeSettings(),
                now = { 10_000L },
                intervalMillis = 1L,
            ),
        )

        assertEquals(
            TodoAutoSyncResult.Completed(TodoRefreshResult.Success(3)),
            dependencies.autoSyncCoordinator.refreshIfDue(),
        )
        assertEquals(1, refresh.calls)
    }

    private class FakeRefresh : TodoRefreshCoordinator {
        var calls = 0

        override suspend fun refresh(): TodoRefreshResult {
            calls += 1
            return TodoRefreshResult.Success(3)
        }
    }

    private class FakeSettings : TodoAutoSyncSettings {
        private var last: Long? = null

        override fun isEnabled(): Boolean = true

        override fun setEnabled(enabled: Boolean) = Unit

        override fun lastSuccessfulSyncAt(): Long? = last

        override fun setLastSuccessfulSyncAt(timestamp: Long) {
            last = timestamp
        }
    }

    private class FakeRepository : TodoRepositoryContract {
        override fun observeTodos(): Flow<List<TodoEntity>> = flowOf(emptyList())

        override suspend fun addTodo(
            title: String,
            description: String,
            deadline: Long?,
            courseId: Long?,
            source: TodoSource,
        ): Long = 1L

        override suspend fun updateTodo(command: UpdateTodoCommand) = Unit

        override suspend fun setCompleted(id: Long, completed: Boolean) = Unit

        override suspend fun deleteTodo(id: Long) = Unit

        override suspend fun getCourseOptions(): List<TodoCourseOption> = emptyList()
    }
}
