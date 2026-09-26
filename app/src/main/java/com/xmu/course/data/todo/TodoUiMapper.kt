package com.xmu.course.data.todo

import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource

/** Todo display path 所需的最小数据，不包含 Room 注解或持久化细节。 */
data class TodoUiModel(
    val id: Long,
    val title: String,
    val description: String,
    val courseId: Long?,
    val source: TodoFeatureSource,
    val deadline: Long?,
    val completed: Boolean,
)

/** 从持久化模型生成展示模型；未知来源按现有 UI 行为回退为本地来源。 */
fun TodoEntity.toUiModel(): TodoUiModel = TodoUiModel(
    id = id,
    title = title,
    description = description,
    courseId = courseId,
    source = if (source == TodoSource.TRONCLASS.name) TodoFeatureSource.EXTERNAL else TodoFeatureSource.LOCAL,
    deadline = deadline,
    completed = completed,
)
