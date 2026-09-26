package com.xmu.course.data.tronclass.feature

import com.xmu.course.data.tronclass.model.TronClassUiError

enum class TronClassAuthUiState {
    AUTHENTICATED,
    UNAUTHENTICATED,
    SESSION_EXPIRED,
}

sealed interface TronClassCourseSyncUiResult {
    data class Success(val count: Int, val lastSyncTime: Long? = null) : TronClassCourseSyncUiResult

    data object SessionExpired : TronClassCourseSyncUiResult

    data class Failed(val error: TronClassUiError) : TronClassCourseSyncUiResult
}

sealed interface TronClassTodoSyncUiResult {
    data class Success(val count: Int) : TronClassTodoSyncUiResult

    data object SessionExpired : TronClassTodoSyncUiResult

    data class Failed(val error: TronClassUiError) : TronClassTodoSyncUiResult
}

sealed interface TronClassLogoutUiResult {
    data object Success : TronClassLogoutUiResult

    data class Failed(val error: TronClassUiError) : TronClassLogoutUiResult
}
