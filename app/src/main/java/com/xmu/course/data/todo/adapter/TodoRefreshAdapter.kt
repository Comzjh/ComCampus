package com.xmu.course.data.todo.adapter

import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.TodoRefreshResult as LegacyTodoRefreshResult

/** 将现有 Todo 刷新协调器包裹为 Feature refresh contract；不改变旧刷新实现。 */
class TodoRefreshAdapter(
    private val delegate: TodoRefreshCoordinator,
) : TodoRefreshReader {
    override suspend fun refresh(): TodoRefreshResult = when (val result = delegate.refresh()) {
        is LegacyTodoRefreshResult.Success -> TodoRefreshResult.Success(result.importedCount)
        LegacyTodoRefreshResult.SessionExpired -> TodoRefreshResult.SessionExpired
        LegacyTodoRefreshResult.Error -> TodoRefreshResult.Error
    }
}
