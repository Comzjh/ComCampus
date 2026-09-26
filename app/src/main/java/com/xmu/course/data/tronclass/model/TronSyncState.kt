package com.xmu.course.data.tronclass.model

/** Repository 对上层暴露的安全同步状态；不携带原始异常或响应内容。 */
sealed interface TronSyncState {
    data object Idle : TronSyncState

    data object CheckingSession : TronSyncState

    data object Syncing : TronSyncState

    data class Success(val count: Int) : TronSyncState

    data class Error(val error: TronClassError) : TronSyncState

    data object SessionExpired : TronSyncState
}

fun <T> TronResult<T>.toSyncState(successCount: (T) -> Int = { 0 }): TronSyncState =
    when (this) {
        is TronResult.Success -> TronSyncState.Success(successCount(value))
        is TronResult.Error -> when (error) {
            TronClassError.Unauthorized -> TronSyncState.SessionExpired
            TronClassError.SessionMissing -> TronSyncState.Error(TronClassError.AuthRequired)
            else -> TronSyncState.Error(error)
        }
    }
