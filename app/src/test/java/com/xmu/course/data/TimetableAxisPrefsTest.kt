package com.xmu.course.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 9.1：时间轴文字偏好（含日期栏字号扩展）。
 * 验证默认值、各键独立存取、损坏数据回退与向后兼容。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableAxisPrefsTest {

    private val axisKeys = listOf(
        "axis_period_font_sp",
        "axis_time_font_sp",
        "axis_date_font_sp",
        "axis_weekday_font_sp",
        "axis_period_color",
        "axis_time_color",
        "axis_weekday_color",
        "axis_date_color",
    )

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearAxisKeys()
        TimetablePrefs.load(context)
    }

    @After
    fun tearDown() {
        clearAxisKeys()
        TimetablePrefs.load(context)
    }

    private fun clearAxisKeys() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit().apply {
            axisKeys.forEach { remove(it) }
        }.commit()
    }

    @Test
    fun `missing prefs fall back to new defaults without crashing`() {
        val style = TimetablePrefs.axisStyle.value
        assertEquals(TimetableAxisStyle.DEFAULT_PERIOD_FONT_SP, style.periodFontSp)
        assertEquals(TimetableAxisStyle.DEFAULT_TIME_FONT_SP, style.timeFontSp)
        assertEquals(TimetableAxisStyle.DEFAULT_DATE_FONT_SP, style.dateFontSp)
        assertEquals(TimetableAxisStyle.DEFAULT_WEEKDAY_FONT_SP, style.weekdayFontSp)
        assertEquals(AxisTextColor.AUTO, style.periodColor)
        assertEquals(AxisTextColor.AUTO, style.timeColor)
        assertEquals(AxisTextColor.AUTO, style.weekdayColor)
        assertEquals(AxisTextColor.AUTO, style.dateColor)
    }

    @Test
    fun `weekday and date default fonts are both 14sp`() {
        assertEquals(14, TimetableAxisStyle.DEFAULT_WEEKDAY_FONT_SP)
        assertEquals(14, TimetableAxisStyle.DEFAULT_DATE_FONT_SP)
    }

    @Test
    fun `axis font ranges are unified across four axes`() {
        assertEquals(TimetableAxisStyle.AXIS_FONT_RANGE, TimetableAxisStyle.periodFontRange)
        assertEquals(TimetableAxisStyle.AXIS_FONT_RANGE, TimetableAxisStyle.timeFontRange)
        assertEquals(TimetableAxisStyle.AXIS_FONT_RANGE, TimetableAxisStyle.dateFontRange)
        assertEquals(TimetableAxisStyle.AXIS_FONT_RANGE, TimetableAxisStyle.weekdayFontRange)
    }

    @Test
    fun `time font size saves and reads independently`() {
        TimetablePrefs.setAxisTimeFontSp(context, 14)
        assertEquals(14, TimetablePrefs.axisStyle.value.timeFontSp)
        TimetablePrefs.load(context)
        assertEquals(14, TimetablePrefs.axisStyle.value.timeFontSp)
    }

    @Test
    fun `period font size saves and reads independently`() {
        TimetablePrefs.setAxisPeriodFontSp(context, 16)
        assertEquals(16, TimetablePrefs.axisStyle.value.periodFontSp)
        TimetablePrefs.load(context)
        assertEquals(16, TimetablePrefs.axisStyle.value.periodFontSp)
    }

    @Test
    fun `time color saves and reads independently`() {
        TimetablePrefs.setAxisTimeColor(context, AxisTextColor.BRAND)
        assertEquals(AxisTextColor.BRAND, TimetablePrefs.axisStyle.value.timeColor)
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.BRAND, TimetablePrefs.axisStyle.value.timeColor)
    }

    @Test
    fun `period color saves and reads independently`() {
        TimetablePrefs.setAxisPeriodColor(context, AxisTextColor.TEAL)
        assertEquals(AxisTextColor.TEAL, TimetablePrefs.axisStyle.value.periodColor)
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.TEAL, TimetablePrefs.axisStyle.value.periodColor)
    }

    @Test
    fun `date font size saves and reads independently`() {
        TimetablePrefs.setAxisDateFontSp(context, 13)
        assertEquals(13, TimetablePrefs.axisStyle.value.dateFontSp)
        TimetablePrefs.load(context)
        assertEquals(13, TimetablePrefs.axisStyle.value.dateFontSp)
    }

    @Test
    fun `changing date font does not touch period or time font`() {
        TimetablePrefs.setAxisPeriodFontSp(context, 15)
        TimetablePrefs.setAxisTimeFontSp(context, 12)
        TimetablePrefs.setAxisDateFontSp(context, 14)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(15, style.periodFontSp)
        assertEquals(12, style.timeFontSp)
        assertEquals(14, style.dateFontSp)
    }

    @Test
    fun `out of range date font is clamped on write and on reload`() {
        TimetablePrefs.setAxisDateFontSp(context, 99)
        assertEquals(TimetableAxisStyle.dateFontRange.last, TimetablePrefs.axisStyle.value.dateFontSp)
        TimetablePrefs.setAxisDateFontSp(context, 2)
        assertEquals(TimetableAxisStyle.dateFontRange.first, TimetablePrefs.axisStyle.value.dateFontSp)
        TimetablePrefs.load(context)
        assertEquals(TimetableAxisStyle.dateFontRange.first, TimetablePrefs.axisStyle.value.dateFontSp)
    }

    @Test
    fun `changing time font does not touch period font`() {
        TimetablePrefs.setAxisPeriodFontSp(context, 15)
        TimetablePrefs.setAxisTimeFontSp(context, 12)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(15, style.periodFontSp)
        assertEquals(12, style.timeFontSp)
    }

    @Test
    fun `changing period color does not touch time color`() {
        TimetablePrefs.setAxisTimeColor(context, AxisTextColor.INK)
        TimetablePrefs.setAxisPeriodColor(context, AxisTextColor.SLATE)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(AxisTextColor.SLATE, style.periodColor)
        assertEquals(AxisTextColor.INK, style.timeColor)
    }

    @Test
    fun `out of range sizes are clamped on write and on reload`() {
        TimetablePrefs.setAxisPeriodFontSp(context, 99)
        TimetablePrefs.setAxisTimeFontSp(context, 1)
        val live = TimetablePrefs.axisStyle.value
        assertEquals(TimetableAxisStyle.periodFontRange.last, live.periodFontSp)
        assertEquals(TimetableAxisStyle.timeFontRange.first, live.timeFontSp)
        TimetablePrefs.load(context)
        val restored = TimetablePrefs.axisStyle.value
        assertEquals(TimetableAxisStyle.periodFontRange.last, restored.periodFontSp)
        assertEquals(TimetableAxisStyle.timeFontRange.first, restored.timeFontSp)
    }

    @Test
    fun `corrupted color key falls back to AUTO`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putString("axis_period_color", "NOT_A_REAL_COLOR")
            .commit()
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.AUTO, TimetablePrefs.axisStyle.value.periodColor)
    }

    @Test
    fun `corrupted date font value falls back within range`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putInt("axis_date_font_sp", 4321)
            .commit()
        TimetablePrefs.load(context)
        assertEquals(TimetableAxisStyle.dateFontRange.last, TimetablePrefs.axisStyle.value.dateFontSp)
    }

    @Test
    fun `corrupted font size value falls back within range`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putInt("axis_time_font_sp", 4321)
            .commit()
        TimetablePrefs.load(context)
        assertEquals(TimetableAxisStyle.timeFontRange.last, TimetablePrefs.axisStyle.value.timeFontSp)
    }

    @Test
    fun `weekday font size saves and reads independently`() {
        TimetablePrefs.setAxisWeekdayFontSp(context, 15)
        assertEquals(15, TimetablePrefs.axisStyle.value.weekdayFontSp)
        TimetablePrefs.load(context)
        assertEquals(15, TimetablePrefs.axisStyle.value.weekdayFontSp)
    }

    @Test
    fun `weekday color saves and reads independently`() {
        TimetablePrefs.setAxisWeekdayColor(context, AxisTextColor.SLATE)
        assertEquals(AxisTextColor.SLATE, TimetablePrefs.axisStyle.value.weekdayColor)
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.SLATE, TimetablePrefs.axisStyle.value.weekdayColor)
    }

    @Test
    fun `date color saves and reads independently`() {
        TimetablePrefs.setAxisDateColor(context, AxisTextColor.BRAND)
        assertEquals(AxisTextColor.BRAND, TimetablePrefs.axisStyle.value.dateColor)
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.BRAND, TimetablePrefs.axisStyle.value.dateColor)
    }

    @Test
    fun `changing weekday font does not touch date font or colors`() {
        TimetablePrefs.setAxisDateFontSp(context, 14)
        TimetablePrefs.setAxisDateColor(context, AxisTextColor.INK)
        TimetablePrefs.setAxisWeekdayFontSp(context, 16)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(16, style.weekdayFontSp)
        assertEquals(14, style.dateFontSp)
        assertEquals(AxisTextColor.INK, style.dateColor)
        assertEquals(AxisTextColor.AUTO, style.weekdayColor)
    }

    @Test
    fun `changing date font does not touch weekday font`() {
        TimetablePrefs.setAxisWeekdayFontSp(context, 13)
        TimetablePrefs.setAxisDateFontSp(context, 15)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(13, style.weekdayFontSp)
        assertEquals(15, style.dateFontSp)
    }

    @Test
    fun `weekday and date colors do not interfere`() {
        TimetablePrefs.setAxisWeekdayColor(context, AxisTextColor.TEAL)
        TimetablePrefs.setAxisDateColor(context, AxisTextColor.SLATE)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(AxisTextColor.TEAL, style.weekdayColor)
        assertEquals(AxisTextColor.SLATE, style.dateColor)
        TimetablePrefs.load(context)
        val restored = TimetablePrefs.axisStyle.value
        assertEquals(AxisTextColor.TEAL, restored.weekdayColor)
        assertEquals(AxisTextColor.SLATE, restored.dateColor)
    }

    @Test
    fun `out of range weekday font is clamped on write and on reload`() {
        TimetablePrefs.setAxisWeekdayFontSp(context, 99)
        assertEquals(TimetableAxisStyle.weekdayFontRange.last, TimetablePrefs.axisStyle.value.weekdayFontSp)
        TimetablePrefs.setAxisWeekdayFontSp(context, 4)
        assertEquals(TimetableAxisStyle.weekdayFontRange.first, TimetablePrefs.axisStyle.value.weekdayFontSp)
        TimetablePrefs.load(context)
        assertEquals(TimetableAxisStyle.weekdayFontRange.first, TimetablePrefs.axisStyle.value.weekdayFontSp)
    }

    @Test
    fun `legacy saved date font 11 is respected after new default change`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putInt("axis_date_font_sp", 11)
            .commit()
        TimetablePrefs.load(context)
        val style = TimetablePrefs.axisStyle.value
        assertEquals(11, style.dateFontSp)
        // 星期栏没有旧键，独立回退新默认 12sp，不受日期栏旧值影响。
        assertEquals(TimetableAxisStyle.DEFAULT_WEEKDAY_FONT_SP, style.weekdayFontSp)
    }

    @Test
    fun `corrupted weekday color falls back to AUTO`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putString("axis_weekday_color", "NOT_A_REAL_COLOR")
            .commit()
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.AUTO, TimetablePrefs.axisStyle.value.weekdayColor)
    }

    @Test
    fun `corrupted date color falls back to AUTO`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putString("axis_date_color", "NOT_A_REAL_COLOR")
            .commit()
        TimetablePrefs.load(context)
        assertEquals(AxisTextColor.AUTO, TimetablePrefs.axisStyle.value.dateColor)
    }

    @Test
    fun `corrupted weekday font value falls back within range`() {
        context.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE).edit()
            .putInt("axis_weekday_font_sp", 4321)
            .commit()
        TimetablePrefs.load(context)
        assertEquals(TimetableAxisStyle.weekdayFontRange.last, TimetablePrefs.axisStyle.value.weekdayFontSp)
    }
}
