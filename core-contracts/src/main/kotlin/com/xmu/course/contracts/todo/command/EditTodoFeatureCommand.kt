package com.xmu.course.contracts.todo.command

import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureId

/** Todo Feature 编辑输入；完成状态与同步标识由 data 层维护。 */
data class EditTodoFeatureCommand(
    val id: TodoFeatureId,
    val title: String,
    val description: String,
    val deadline: Long?,
    val courseReference: TodoCourseReference?,
)
