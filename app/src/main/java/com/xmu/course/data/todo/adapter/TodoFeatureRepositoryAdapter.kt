package com.xmu.course.data.todo.adapter

import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.data.todo.TodoCourseOption
import com.xmu.course.data.todo.TodoRepositoryContract
import com.xmu.course.data.todo.UpdateTodoCommand
import com.xmu.course.data.todo.model.CreateTodoCommand
import com.xmu.course.data.todo.model.EditTodoCommand
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.data.todo.toFeatureModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 将现有 TodoRepositoryContract 适配为 feature contract；不改变旧数据路径。 */
class TodoFeatureRepositoryAdapter(
    private val delegate: TodoRepositoryContract,
) : TodoFeatureRepository {
    override fun observeTodos(): Flow<List<TodoFeatureModel>> =
        delegate.observeTodos().map { todos -> todos.map { it.toFeatureModel() } }

    override suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId = TodoFeatureId(delegate.addTodo(
        title = command.title,
        description = command.description,
        deadline = command.deadline,
        courseId = command.courseReference?.id,
        source = command.courseReference.toDataSource(),
    ))

    override suspend fun updateTodo(command: EditTodoFeatureCommand) {
        delegate.updateTodo(
            UpdateTodoCommand(
                id = command.id.value,
                title = command.title,
                description = command.description,
                deadline = command.deadline,
                courseId = command.courseReference?.id,
                source = command.courseReference.toDataSource(),
            ),
        )
    }

    override suspend fun setCompleted(id: TodoFeatureId, completed: Boolean) =
        delegate.setCompleted(id.value, completed)

    override suspend fun deleteTodo(id: TodoFeatureId) = delegate.deleteTodo(id.value)

    override suspend fun getCourseOptions(): List<TodoCourseOptionModel> =
        delegate.getCourseOptions().map(TodoCourseOption::toFeatureModel)
}

private fun TodoCourseReference?.toDataSource(): TodoSource = when (this) {
    null, is TodoCourseReference.Local -> TodoSource.LOCAL
    is TodoCourseReference.External -> TodoSource.TRONCLASS
}
