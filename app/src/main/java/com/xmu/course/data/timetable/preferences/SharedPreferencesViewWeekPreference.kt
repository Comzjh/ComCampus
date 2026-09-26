package com.xmu.course.data.timetable.preferences

import android.content.Context
import com.xmu.course.contracts.timetable.ViewWeekPreference
import com.xmu.course.data.TimetablePrefs

/** SharedPreferences-backed implementation for the Timetable view-week capability. */
class SharedPreferencesViewWeekPreference(
    context: Context,
) : ViewWeekPreference {

    private val appContext = context.applicationContext

    override fun getViewWeek(timetableId: Long): Int? =
        TimetablePrefs.getViewWeek(appContext, timetableId)

    override fun setViewWeek(timetableId: Long, week: Int) {
        TimetablePrefs.setViewWeek(appContext, timetableId, week)
    }
}
