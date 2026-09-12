package com.xmu.course.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前使用课表 ID（SharedPreferences 持久化 + StateFlow 响应式）。
 *
 * 不进 Room：只保存"用户当前打开哪个课表"这一 UI 状态。
 */
object TimetablePrefs {

    private const val PREFS = "timetable_prefs"
    private const val KEY_CURRENT_TIMETABLE = "current_timetable_id"
    private const val KEY_VIEW_WEEK_PREFIX = "view_week_"

    private val _currentTimetableId = MutableStateFlow<Long?>(null)
    val currentTimetableId: StateFlow<Long?> = _currentTimetableId.asStateFlow()

    /** App 启动时调用一次，从 prefs 恢复。 */
    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_CURRENT_TIMETABLE, -1L)
        _currentTimetableId.value = saved.takeIf { it > 0 }
    }

    /** 读取某课表保存的查看周（未保存返回 null，调用方回退实际周）。 */
    fun getViewWeek(context: Context, timetableId: Long): Int? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VIEW_WEEK_PREFIX + timetableId, -1)
        return saved.takeIf { it > 0 }
    }

    /** 保存某课表的查看周（每课表独立，切回时恢复）。 */
    fun setViewWeek(context: Context, timetableId: Long, week: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_VIEW_WEEK_PREFIX + timetableId, week).apply()
    }

    /** 切换当前课表；传 null 表示清除（例如删除了当前课表）。 */
    fun setCurrent(context: Context, id: Long?) {
        _currentTimetableId.value = id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (id == null) remove(KEY_CURRENT_TIMETABLE) else putLong(KEY_CURRENT_TIMETABLE, id)
        }.apply()
    }
}
