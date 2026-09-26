package com.xmu.course.contracts.todo.model

/** Todo 编辑器选择关联课程时需要的最小 Feature 模型。 */
data class TodoCourseOptionModel(
    val reference: TodoCourseReference,
    val name: String,
    val teacher: String,
)
