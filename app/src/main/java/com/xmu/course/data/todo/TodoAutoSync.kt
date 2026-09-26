package com.xmu.course.data.todo

import android.content.Context
import com.xmu.course.data.readSafely

interface TodoAutoSyncSettings {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)

    fun lastSuccessfulSyncAt(): Long?

    fun setLastSuccessfulSyncAt(timestamp: Long)
}

class SharedPreferencesTodoAutoSyncSettings(context: Context) : TodoAutoSyncSettings {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun isEnabled(): Boolean = preferences.readSafely(false) {
        getBoolean(KEY_ENABLED, true)
    }

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    override fun lastSuccessfulSyncAt(): Long? =
        preferences.readSafely(System.currentTimeMillis()) {
            getLong(KEY_LAST_SUCCESS, 0L)
        }.takeIf { it > 0L }

    override fun setLastSuccessfulSyncAt(timestamp: Long) {
        preferences.edit().putLong(KEY_LAST_SUCCESS, timestamp).apply()
    }

    private companion object {
        const val PREFERENCES = "todo_auto_sync"
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_SUCCESS = "last_successful_sync_at"
    }
}

sealed interface TodoAutoSyncResult {
    data object SkippedDisabled : TodoAutoSyncResult
    data object SkippedUnauthenticated : TodoAutoSyncResult
    data object SkippedThrottled : TodoAutoSyncResult
    data class Completed(val result: TodoRefreshResult) : TodoAutoSyncResult
}

/** 前台触发的节流同步；不创建后台调度器，不负责倒计时显示。 */
class TodoAutoSyncCoordinator(
    private val refreshCoordinator: TodoRefreshCoordinator,
    private val settings: TodoAutoSyncSettings,
    private val now: () -> Long = System::currentTimeMillis,
    private val intervalMillis: Long = 60L * 60L * 1000L,
    private val isSessionAvailable: () -> Boolean = { true },
) {
    suspend fun refreshIfDue(): TodoAutoSyncResult {
        if (!settings.isEnabled()) return TodoAutoSyncResult.SkippedDisabled
        if (!isSessionAvailable()) return TodoAutoSyncResult.SkippedUnauthenticated
        val current = now()
        val last = settings.lastSuccessfulSyncAt()
        if (last != null && current - last < intervalMillis) {
            return TodoAutoSyncResult.SkippedThrottled
        }
        val result = refreshCoordinator.refresh()
        if (result is TodoRefreshResult.Success) {
            settings.setLastSuccessfulSyncAt(current)
        }
        return TodoAutoSyncResult.Completed(result)
    }
}
