package com.xmu.course.data.tronclass.assignment

import android.content.Context

/** 作业自动导入设置的抽象；UI 和同步编排不绑定 SharedPreferences 细节。 */
interface AssignmentSyncSettings {
    fun isAutoImportEnabled(): Boolean
    fun setAutoImportEnabled(enabled: Boolean)
}

class SharedPreferencesAssignmentSyncSettings(context: Context) : AssignmentSyncSettings {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * TRON-08：默认开启。
     *
     * 只改变「从未写过这个键」时的取值；用户显式关闭后持久化的 false 依旧生效，
     * 因此不需要任何迁移，也不会把已有用户的选择盖掉。
     */
    override fun isAutoImportEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTO_IMPORT, true)

    override fun setAutoImportEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_IMPORT, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "tronclass_settings"
        const val KEY_AUTO_IMPORT = "auto_import_assignments"
    }
}

object DisabledAssignmentSyncSettings : AssignmentSyncSettings {
    override fun isAutoImportEnabled(): Boolean = false
    override fun setAutoImportEnabled(enabled: Boolean) = Unit
}
