package com.xmu.course.contracts.todo

import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import kotlinx.coroutines.flow.Flow

/** Todo Feature 的能力边界；具体数据实现由 app/data 层通过 Adapter 提供。 */
interface TodoFeatureRepository {
    fun observeTodos(): Flow<List<TodoFeatureModel>>

    suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId

    suspend fun updateTodo(command: EditTodoFeatureCommand)

    suspend fun setCompleted(id: TodoFeatureId, completed: Boolean)

    suspend fun deleteTodo(id: TodoFeatureId)

    suspend fun getCourseOptions(): List<TodoCourseOptionModel>
}
