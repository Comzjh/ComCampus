package com.xmu.course.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 课表显示设置（SharedPreferences 持久化 + StateFlow 响应式）。
 */
object DisplaySettings {

    private const val PREFS = "display_settings"
    private const val KEY_SHOW_GRID = "show_grid"
    private const val KEY_ONLY_CURRENT = "only_current_week"
    private const val KEY_HIDE_WEEKEND = "hide_weekend"

    private val _showGrid = MutableStateFlow(true)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    private val _onlyCurrentWeek = MutableStateFlow(false)
    val onlyCurrentWeek: StateFlow<Boolean> = _onlyCurrentWeek.asStateFlow()

    private val _hideWeekend = MutableStateFlow(false)
    val hideWeekend: StateFlow<Boolean> = _hideWeekend.asStateFlow()

    /** App 启动时调用一次，从 prefs 恢复。 */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _showGrid.value = prefs.readSafely(true) { getBoolean(KEY_SHOW_GRID, true) }
        _onlyCurrentWeek.value = prefs.readSafely(false) { getBoolean(KEY_ONLY_CURRENT, false) }
        _hideWeekend.value = prefs.readSafely(false) { getBoolean(KEY_HIDE_WEEKEND, false) }
    }

    fun setShowGrid(context: Context, value: Boolean) {
        _showGrid.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_GRID, value).apply()
    }

    fun setOnlyCurrentWeek(context: Context, value: Boolean) {
        _onlyCurrentWeek.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONLY_CURRENT, value).apply()
    }

    fun setHideWeekend(context: Context, value: Boolean) {
        _hideWeekend.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_WEEKEND, value).apply()
    }
}
