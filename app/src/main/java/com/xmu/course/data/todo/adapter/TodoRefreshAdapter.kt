package com.xmu.course.data.todo.adapter

import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.TodoAutoSyncCoordinator
import com.xmu.course.data.todo.TodoAutoSyncResult
import com.xmu.course.data.todo.TodoRefreshResult as LegacyTodoRefreshResult

/** 将现有 Todo 刷新协调器包裹为 Feature refresh contract；不改变旧刷新实现。 */
class TodoRefreshAdapter(
    private val delegate: TodoRefreshCoordinator,
) : TodoRefreshReader {
    /** App手动刷新与前台/Widget共用同一互斥与成功时间。 */
    constructor(coordinator: TodoAutoSyncCoordinator) : this(TodoRefreshCoordinator {
        when (val result = coordinator.refreshNow()) {
            is TodoAutoSyncResult.Completed -> result.result
            TodoAutoSyncResult.SkippedUnauthenticated -> LegacyTodoRefreshResult.SessionExpired
            TodoAutoSyncResult.SkippedDisabled, TodoAutoSyncResult.SkippedThrottled -> LegacyTodoRefreshResult.Error
        }
    })

    override suspend fun refresh(): TodoRefreshResult = when (val result = delegate.refresh()) {
        is LegacyTodoRefreshResult.Success -> TodoRefreshResult.Success(result.importedCount)
        LegacyTodoRefreshResult.SessionExpired -> TodoRefreshResult.SessionExpired
        LegacyTodoRefreshResult.Error -> TodoRefreshResult.Error
    }
}
