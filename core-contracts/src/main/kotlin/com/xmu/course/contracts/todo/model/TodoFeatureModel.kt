package com.xmu.course.contracts.todo.model

/** Todo Feature 使用的最小展示/编辑模型，不包含 Room 持久化字段。 */
data class TodoFeatureModel(
    val id: TodoFeatureId,
    val title: String,
    val description: String,
    val courseReference: TodoCourseReference?,
    val source: TodoFeatureSource,
    val deadline: Long?,
    val completed: Boolean,
)
