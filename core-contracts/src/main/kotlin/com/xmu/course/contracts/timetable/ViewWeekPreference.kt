package com.xmu.course.contracts.timetable

/**
 * Timetable 页面按课表保存的浏览周偏好。
 *
 * 这是 presentation-facing capability，不包含 Android Context 或具体持久化实现。
 */
interface ViewWeekPreference {
    fun getViewWeek(timetableId: Long): Int?

    fun setViewWeek(timetableId: Long, week: Int)
}
