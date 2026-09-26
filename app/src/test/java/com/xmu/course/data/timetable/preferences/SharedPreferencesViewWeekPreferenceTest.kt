package com.xmu.course.data.timetable.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesViewWeekPreferenceTest {

    @Test
    fun `wrapper preserves TimetablePrefs view week storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timetableId = 987_654_321L
        val preferences = context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)
        preferences.edit().remove("view_week_$timetableId").commit()

        try {
            val viewWeekPreference = SharedPreferencesViewWeekPreference(context)

            assertNull(viewWeekPreference.getViewWeek(timetableId))
            viewWeekPreference.setViewWeek(timetableId, 8)
            assertEquals(8, viewWeekPreference.getViewWeek(timetableId))
            assertEquals(8, TimetablePrefs.getViewWeek(context, timetableId))
        } finally {
            preferences.edit().remove("view_week_$timetableId").commit()
        }
    }
}
