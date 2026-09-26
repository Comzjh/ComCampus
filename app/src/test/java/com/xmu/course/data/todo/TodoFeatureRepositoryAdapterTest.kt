package com.xmu.course.data.todo

import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.data.todo.adapter.TodoFeatureRepositoryAdapter
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoFeatureRepositoryAdapterTest {
    @Test
    fun `adapter maps reads and course options to feature models`() = runTest {
        val delegate = FakeRepository()
        delegate.todos.value = listOf(
            TodoEntity(
                id = 7L,
                title = "待办",
                description = "描述",
                courseId = 42L,
                source = TodoSource.TRONCLASS.name,
                deadline = 99L,
                completed = false,
                createdTime = 1L,
                updatedTime = 2L,
                externalId = "remote-7",
            ),
        )
        delegate.options = listOf(TodoCourseOption(42L, "课程", "教师", TodoSource.TRONCLASS))

        val repository: TodoFeatureRepository = TodoFeatureRepositoryAdapter(delegate)

        assertEquals("待办", repository.observeTodos().first().single().title)
        assertEquals(TodoFeatureSource.EXTERNAL, repository.observeTodos().first().single().source)
        assertEquals(
            TodoCourseOptionModel(TodoCourseReference.External(42L), "课程", "教师"),
            repository.getCourseOptions().single(),
        )
    }

    @Test
    fun `adapter maps feature writes and delegates id operations`() = runTest {
        val delegate = FakeRepository()
        val repository: TodoFeatureRepository = TodoFeatureRepositoryAdapter(delegate)

        repository.addTodo(CreateTodoFeatureCommand("新增", "描述", 3L, TodoCourseReference.External(42L)))
        repository.updateTodo(EditTodoFeatureCommand(TodoFeatureId(7L), "修改", "新描述", 4L, TodoCourseReference.Local(43L)))
        repository.setCompleted(TodoFeatureId(7L), true)
        repository.deleteTodo(TodoFeatureId(7L))

        assertEquals(TodoSource.TRONCLASS, delegate.lastAddSource)
        assertEquals(UpdateTodoCommand(7L, "修改", "新描述", 4L, 43L, TodoSource.LOCAL), delegate.lastUpdate)
        assertEquals(7L to true, delegate.lastCompleted)
        assertEquals(7L, delegate.lastDeleted)
    }

    private class FakeRepository : TodoRepositoryContract {
        val todos = MutableStateFlow<List<TodoEntity>>(emptyList())
        var options = emptyList<TodoCourseOption>()
        var lastAddSource: TodoSource? = null
        var lastUpdate: UpdateTodoCommand? = null
        var lastCompleted: Pair<Long, Boolean>? = null
        var lastDeleted: Long? = null

        override fun observeTodos(): Flow<List<TodoEntity>> = todos

        override suspend fun addTodo(
            title: String,
            description: String,
            deadline: Long?,
            courseId: Long?,
            source: TodoSource,
        ): Long {
            lastAddSource = source
            return 1L
        }

        override suspend fun updateTodo(command: UpdateTodoCommand) {
            lastUpdate = command
        }

        override suspend fun setCompleted(id: Long, completed: Boolean) {
            lastCompleted = id to completed
        }

        override suspend fun deleteTodo(id: Long) {
            lastDeleted = id
        }

        override suspend fun getCourseOptions(): List<TodoCourseOption> = options
    }
}
