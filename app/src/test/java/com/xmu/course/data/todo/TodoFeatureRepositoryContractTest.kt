package com.xmu.course.data.todo

import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoFeatureRepositoryContractTest {
    @Test
    fun `contract exposes only feature models and commands`() {
        val fake = object : TodoFeatureRepository {
            override fun observeTodos(): Flow<List<TodoFeatureModel>> = flowOf(emptyList())

            override suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId =
                TodoFeatureId(command.courseReference?.id ?: 0L)

            override suspend fun updateTodo(command: EditTodoFeatureCommand) = Unit

            override suspend fun setCompleted(id: TodoFeatureId, completed: Boolean) = Unit

            override suspend fun deleteTodo(id: TodoFeatureId) = Unit

            override suspend fun getCourseOptions(): List<TodoCourseOptionModel> = emptyList()
        }

        assertEquals(TodoFeatureId(7L), kotlinx.coroutines.runBlocking {
            fake.addTodo(CreateTodoFeatureCommand("标题", "", null, TodoCourseReference.Local(7L)))
        })
    }
}
