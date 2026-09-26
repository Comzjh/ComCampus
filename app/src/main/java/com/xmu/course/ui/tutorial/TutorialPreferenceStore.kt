package com.xmu.course.ui.tutorial

import android.content.Context

/**
 * Tracks which tutorial versions the user has completed.
 * Only used to control the discovery hint; never hides the help button.
 */
interface TutorialPreferenceStore {
    fun hasCompleted(seenKey: String): Boolean

    fun markCompleted(seenKey: String)

    /** 教程入口是否被用户收起；首次使用默认收起，明确选择的状态会持久化。 */
    fun isLauncherCollapsed(): Boolean = true

    fun setLauncherCollapsed(collapsed: Boolean) = Unit
}

class SharedPreferencesTutorialPreferenceStore(context: Context) : TutorialPreferenceStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasCompleted(seenKey: String): Boolean {
        return prefs.getBoolean(seenKey, false)
    }

    override fun markCompleted(seenKey: String) {
        prefs.edit().putBoolean(seenKey, true).apply()
    }

    override fun isLauncherCollapsed(): Boolean =
        prefs.getBoolean(KEY_LAUNCHER_COLLAPSED, true)

    override fun setLauncherCollapsed(collapsed: Boolean) {
        prefs.edit().putBoolean(KEY_LAUNCHER_COLLAPSED, collapsed).apply()
    }

    private companion object {
        const val PREFS_NAME = "tutorial_prefs"
        const val KEY_LAUNCHER_COLLAPSED = "tutorial_launcher_collapsed"
    }
}
