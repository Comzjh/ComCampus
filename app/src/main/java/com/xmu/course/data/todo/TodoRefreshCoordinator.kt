package com.xmu.course.data.todo

/** Todo 页面主动刷新外部待办的边界，不向 UI 泄漏认证/API 异常。 */
sealed interface TodoRefreshResult {
    data class Success(val importedCount: Int) : TodoRefreshResult
    data object SessionExpired : TodoRefreshResult
    data object Error : TodoRefreshResult
}

fun interface TodoRefreshCoordinator {
    suspend fun refresh(): TodoRefreshResult
}
