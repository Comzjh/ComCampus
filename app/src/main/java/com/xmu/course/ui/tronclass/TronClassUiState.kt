package com.xmu.course.ui.tronclass

import com.xmu.course.data.tronclass.model.TronCourseUiModel
import com.xmu.course.data.tronclass.model.TronClassUiError
import com.xmu.course.data.tronclass.model.toSafeMessage

enum class TronClassScreenState {
    CheckingSession,
    Unauthenticated,
    Authenticated,
    Syncing,
    Success,
    Error,
    SessionExpired,
}

data class TronClassUiState(
    val screenState: TronClassScreenState = TronClassScreenState.CheckingSession,
    val courses: List<TronCourseUiModel> = emptyList(),
    val lastSyncTime: Long? = null,
    val lastSyncCount: Int? = null,
    val lastAssignmentSyncCount: Int? = null,
    val error: TronClassUiError? = null,
    val showLogoutConfirmation: Boolean = false,
    val clearWebDataRequested: Boolean = false,
    /** 一次性同步完成令牌：每次成功同步 +1，导航层据此只回跳一次（BUG-02）。 */
    val syncCompletionToken: Long = 0L,
)
