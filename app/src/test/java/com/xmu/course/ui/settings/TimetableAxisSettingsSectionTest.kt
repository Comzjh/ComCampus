package com.xmu.course.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.data.AxisTextColor
import com.xmu.course.data.TimetableAxisStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 课表文字设置区（Phase 9.1 起，Phase 11 扩展星期栏/日期栏）。
 * 验证四栏字号/颜色控件可见、默认值展示与回调独立。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableAxisSettingsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun showSection(
        onPeriodFontChange: (Int) -> Unit = {},
        onTimeFontChange: (Int) -> Unit = {},
        onDateFontChange: (Int) -> Unit = {},
        onPeriodColorChange: (AxisTextColor) -> Unit = {},
        onTimeColorChange: (AxisTextColor) -> Unit = {},
        onWeekdayFontChange: (Int) -> Unit = {},
        onWeekdayColorChange: (AxisTextColor) -> Unit = {},
        onDateColorChange: (AxisTextColor) -> Unit = {},
    ) {
        composeRule.setContent {
            // 真实设置页外层可滚动；测试补同样的滚动容器，验证全部控件可达。
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimetableAxisSettingsSection(
                    axisStyle = TimetableAxisStyle(),
                    onPeriodFontChange = onPeriodFontChange,
                    onTimeFontChange = onTimeFontChange,
                    onDateFontChange = onDateFontChange,
                    onPeriodColorChange = onPeriodColorChange,
                    onTimeColorChange = onTimeColorChange,
                    onWeekdayFontChange = onWeekdayFontChange,
                    onWeekdayColorChange = onWeekdayColorChange,
                    onDateColorChange = onDateColorChange,
                )
            }
        }
    }    @Test
    fun `all timetable text settings are visible`() {
        showSection()
        composeRule.onNodeWithText("课表文字").assertIsDisplayed()
        composeRule.onNodeWithText("节次栏字号").assertIsDisplayed()
        composeRule.onNodeWithText("节次栏颜色").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("时间栏字号").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("时间栏颜色").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("星期栏字号").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("星期栏颜色").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("日期栏字号").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("日期栏颜色").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `old timeline title is gone`() {
        showSection()
        composeRule.onNodeWithText("时间轴文字").assertDoesNotExist()
    }

    @Test
    fun `default sizes are shown in slider labels`() {
        showSection()
        // Phase 16：四栏默认同为 14sp，滑块标签一致，确认默认值各自生效。
        composeRule.onAllNodesWithText("${TimetableAxisStyle.DEFAULT_PERIOD_FONT_SP}sp")
            .assertCountEquals(4)
        assertEquals(14, TimetableAxisStyle.DEFAULT_WEEKDAY_FONT_SP)
        assertEquals(14, TimetableAxisStyle.DEFAULT_DATE_FONT_SP)
    }

    @Test
    fun `tapping period swatch reports only the period color change`() {
        var period: AxisTextColor? = null
        var time: AxisTextColor? = null
        var weekday: AxisTextColor? = null
        var date: AxisTextColor? = null
        showSection(
            onPeriodColorChange = { period = it },
            onTimeColorChange = { time = it },
            onWeekdayColorChange = { weekday = it },
            onDateColorChange = { date = it },
        )
        composeRule.onNodeWithTag("axis_period_color_brand").performClick()
        assertEquals(AxisTextColor.BRAND, period)
        assertEquals(null, time)
        assertEquals(null, weekday)
        assertEquals(null, date)
    }

    @Test
    fun `tapping weekday swatch reports only the weekday color change`() {
        var period: AxisTextColor? = null
        var time: AxisTextColor? = null
        var weekday: AxisTextColor? = null
        var date: AxisTextColor? = null
        showSection(
            onPeriodColorChange = { period = it },
            onTimeColorChange = { time = it },
            onWeekdayColorChange = { weekday = it },
            onDateColorChange = { date = it },
        )
        composeRule.onNodeWithTag("axis_weekday_color_ink").performScrollTo().performClick()
        assertEquals(AxisTextColor.INK, weekday)
        assertEquals(null, period)
        assertEquals(null, time)
        assertEquals(null, date)
    }

    @Test
    fun `tapping date swatch reports only the date color change`() {
        var weekday: AxisTextColor? = null
        var date: AxisTextColor? = null
        showSection(
            onWeekdayColorChange = { weekday = it },
            onDateColorChange = { date = it },
        )
        composeRule.onNodeWithTag("axis_date_color_teal").performScrollTo().performClick()
        assertEquals(AxisTextColor.TEAL, date)
        assertEquals(null, weekday)
    }

    @Test
    fun `tapping date auto swatch does not affect weekday color`() {
        var weekday: AxisTextColor? = null
        var date: AxisTextColor? = null
        showSection(
            onWeekdayColorChange = { weekday = it },
            onDateColorChange = { date = it },
        )
        composeRule.onNodeWithTag("axis_date_color_auto").performScrollTo().performClick()
        assertEquals(AxisTextColor.AUTO, date)
        assertEquals(null, weekday)
    }
}
