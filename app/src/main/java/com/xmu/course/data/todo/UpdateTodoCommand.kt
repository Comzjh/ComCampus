package com.xmu.course.data.todo

import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource

/**
 * Todo 编辑操作的最小输入。
 *
 * 持久化字段（completed、createdTime、externalId 等）由已有记录提供，
 * 不允许由编辑表单覆盖。
 */
data class UpdateTodoCommand(
    val id: Long,
    val title: String,
    val description: String,
    val deadline: Long?,
    val courseId: Long?,
    val source: TodoSource,
)

/** 将编辑命令合并到已有记录，保留当前 updateTodo 的字段语义。 */
object TodoUpdateMapper {
    fun applyTo(existing: TodoEntity, command: UpdateTodoCommand, updatedTime: Long): TodoEntity {
        require(existing.id == command.id) { "Todo 编辑命令与已有记录不匹配" }
        require(command.title.isNotBlank()) { "待办标题不能为空" }
        return existing.copy(
            title = command.title.trim(),
            description = command.description,
            deadline = command.deadline,
            courseId = command.courseId,
            source = command.source.name,
            updatedTime = updatedTime,
        )
    }
}
