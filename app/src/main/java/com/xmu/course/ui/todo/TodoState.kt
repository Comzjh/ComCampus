package com.xmu.course.ui.todo

import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureModel

/** 当前进程内最近一次成功同步的摘要；不声称是持久化历史。 */
data class TodoSyncSummary(
    val importedCount: Int,
    val automatic: Boolean,
)

sealed interface TodoState {
    data object Loading : TodoState

    data class Empty(
        val courseOptions: List<TodoCourseOptionModel> = emptyList(),
    ) : TodoState

    data class Success(
        val todos: List<TodoFeatureModel>,
        val courseOptions: List<TodoCourseOptionModel> = emptyList(),
    ) : TodoState

    data class Error(
        val message: String,
        val todos: List<TodoFeatureModel> = emptyList(),
        val courseOptions: List<TodoCourseOptionModel> = emptyList(),
    ) : TodoState
}
