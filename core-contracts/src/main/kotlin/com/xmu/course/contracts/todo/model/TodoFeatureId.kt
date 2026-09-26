package com.xmu.course.contracts.todo.model

/**
 * Todo feature 的不透明身份。
 *
 * 当前实现仍由 Adapter 映射到本地 Room 行 id；该细节不应成为 Feature API 的裸 Long 语义。
 */
@JvmInline
value class TodoFeatureId(val value: Long)
