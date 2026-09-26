package com.xmu.course.contracts.todo.command

import com.xmu.course.contracts.todo.model.TodoCourseReference

/** Todo Feature 创建输入，不包含存储、同步或 Provider 字段。 */
data class CreateTodoFeatureCommand(
    val title: String,
    val description: String,
    val deadline: Long?,
    val courseReference: TodoCourseReference?,
)
