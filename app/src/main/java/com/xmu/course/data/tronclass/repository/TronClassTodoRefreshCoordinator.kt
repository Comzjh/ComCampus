package com.xmu.course.data.tronclass.repository

import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.TodoRefreshResult
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.tronclass.model.TronSyncState

/** Todo 页面手动刷新：先更新课程缓存，再独立同步 homework 与官方 exam 待办。 */
class TronClassTodoRefreshCoordinator(
    private val repository: TronClassRepositoryContract,
) : TodoRefreshCoordinator {
    override suspend fun refresh(): TodoRefreshResult {
        val courseSyncState = repository.syncCoursesState()
        return when (courseSyncState) {
            TronSyncState.SessionExpired -> TodoRefreshResult.SessionExpired
            is TronSyncState.Error -> if (repositoryErrorNeedsAuth(courseSyncState.error)) {
                TodoRefreshResult.SessionExpired
            } else {
                TodoRefreshResult.Error
            }
            is TronSyncState.Success -> when (val todos = repository.syncTodoSources()) {
                is TronResult.Success -> TodoRefreshResult.Success(todos.value.totalCount)
                is TronResult.Error -> todos.error.toTodoRefreshResult()
            }
            TronSyncState.Idle,
            TronSyncState.CheckingSession,
            TronSyncState.Syncing,
            -> TodoRefreshResult.Error
        }
    }

}

private fun TronClassError.toTodoRefreshResult(): TodoRefreshResult =
    if (this == TronClassError.Unauthorized ||
        this == TronClassError.SessionMissing ||
        this == TronClassError.AuthRequired
    ) {
        TodoRefreshResult.SessionExpired
    } else {
        TodoRefreshResult.Error
    }

private fun repositoryErrorNeedsAuth(error: TronClassError): Boolean =
    error == TronClassError.AuthRequired || error == TronClassError.SessionMissing
