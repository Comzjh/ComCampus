package com.xmu.course.data.todo.model

import com.xmu.course.contracts.todo.model.TodoFeatureSource

/** Adapter 专用的 legacy 创建写入参数；不属于 Feature API。 */
internal data class CreateTodoCommand(
    val title: String,
    val description: String,
    val deadline: Long?,
    val courseId: Long?,
    val source: TodoFeatureSource,
)

/** Adapter 专用的 legacy 编辑写入参数；不属于 Feature API。 */
internal data class EditTodoCommand(
    val id: Long,
    val title: String,
    val description: String,
    val deadline: Long?,
    val courseId: Long?,
    val source: TodoFeatureSource,
)
