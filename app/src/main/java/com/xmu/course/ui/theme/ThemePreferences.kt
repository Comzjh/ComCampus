package com.xmu.course.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外观偏好的持久化（UI 层本地实现，仿 data/DisplaySettings 模式，不触碰数据层）。
 *
 * followSystemColor = true 时启用 Android 12+ 动态取色（Material You）；
 * 默认 false，即 ComCampus 品牌深蓝（Phase 8 决策①C）。
 */
object ThemePreferences {

    private const val PREFS = "app_theme"
    private const val KEY_FOLLOW_SYSTEM_COLOR = "follow_system_color"

    private val _followSystemColor = MutableStateFlow(false)
    val followSystemColor: StateFlow<Boolean> = _followSystemColor.asStateFlow()

    @Volatile
    private var loaded = false

    /** 首次使用时从 SharedPreferences 恢复；后续调用零成本。 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _followSystemColor.value = try {
                prefs.getBoolean(KEY_FOLLOW_SYSTEM_COLOR, false)
            } catch (_: ClassCastException) {
                false
            }
            loaded = true
        }
    }

    fun setFollowSystemColor(context: Context, value: Boolean) {
        ensureLoaded(context)
        _followSystemColor.value = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FOLLOW_SYSTEM_COLOR, value).apply()
    }
}
