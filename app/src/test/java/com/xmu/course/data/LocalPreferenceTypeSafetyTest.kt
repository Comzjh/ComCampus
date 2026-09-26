package com.xmu.course.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPreferenceTypeSafetyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        DisplaySettings.load(context)
        TimetablePrefs.load(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        DisplaySettings.load(context)
        TimetablePrefs.load(context)
    }

    @Test
    fun wrongDisplaySettingTypesFallBackWithoutRewritingStoredValues() {
        val prefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("show_grid", "invalid")
            .putInt("only_current_week", 1)
            .putString("hide_weekend", "invalid")
            .commit()

        DisplaySettings.load(context)

        assertEquals(true, DisplaySettings.showGrid.value)
        assertEquals(false, DisplaySettings.onlyCurrentWeek.value)
        assertEquals(false, DisplaySettings.hideWeekend.value)
        assertEquals("invalid", prefs.all["show_grid"])
        assertEquals(1, prefs.all["only_current_week"])
        assertEquals("invalid", prefs.all["hide_weekend"])
    }

    @Test
    fun wrongTimetablePreferenceTypesFallBackWithoutRewritingStoredValues() {
        val prefs = context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("current_timetable_id", "invalid")
            .putBoolean("axis_period_font_sp", false)
            .putInt("axis_time_font_sp", 20)
            .putString("axis_date_font_sp", "invalid")
            .putBoolean("axis_weekday_font_sp", true)
            .putBoolean("axis_period_color", false)
            .putString("view_week_3", "invalid")
            .commit()

        TimetablePrefs.load(context)

        assertNull(TimetablePrefs.currentTimetableId.value)
        assertNull(TimetablePrefs.getViewWeek(context, 3L))
        assertEquals(
            TimetableAxisStyle(
                timeFontSp = 18,
            ),
            TimetablePrefs.axisStyle.value,
        )
        assertEquals("invalid", prefs.all["current_timetable_id"])
        assertEquals(false, prefs.all["axis_period_font_sp"])
        assertEquals(20, prefs.all["axis_time_font_sp"])
        assertEquals("invalid", prefs.all["axis_date_font_sp"])
        assertEquals(true, prefs.all["axis_weekday_font_sp"])
        assertEquals(false, prefs.all["axis_period_color"])
        assertEquals("invalid", prefs.all["view_week_3"])
    }
}
