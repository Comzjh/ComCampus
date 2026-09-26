package com.xmu.course.ui.timetable

import com.xmu.course.data.TimetableAxisStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9.1：时间轴排版纯函数。验证新默认值、用户基准缩放与最大字号布局安全。
 */
class TimetableAxisTypographyTest {

    @Test
    fun `defaults match unified 14sp axis style`() {
        val standard = timeAxisTypography(62f)
        // Phase 16：默认基准与 TimetableAxisStyle 常量一致（14sp）。
        assertTrue(standard.numberSizeSp == TimetableAxisStyle.DEFAULT_PERIOD_FONT_SP)
        assertTrue(standard.timeSizeSp == TimetableAxisStyle.DEFAULT_TIME_FONT_SP)
    }

    @Test
    fun `larger user base yields larger standard size`() {
        val bigger = timeAxisTypography(62f, periodBaseSp = 18, timeBaseSp = 16)
        val standard = timeAxisTypography(62f)
        assertTrue(bigger.numberSizeSp > standard.numberSizeSp)
        assertTrue(bigger.timeSizeSp > standard.timeSizeSp)
    }

    @Test
    fun `small cells still shrink proportionally below user base`() {
        val compact = timeAxisTypography(50f, periodBaseSp = 18, timeBaseSp = 16)
        assertTrue(compact.numberSizeSp < 18)
        assertTrue(compact.timeSizeSp < 16)
    }

    @Test
    fun `max user sizes fit the smallest cell without clipping`() {
        // 节次行高 + 两行时间行高 必须不超过格子高度，任何合法组合都不裁切。
        val maxAtLowest = timeAxisTypography(
            44f,
            TimetableAxisStyle.periodFontRange.last,
            TimetableAxisStyle.timeFontRange.last,
        )
        assertTrue(
            maxAtLowest.numberLineHeightSp + 2 * maxAtLowest.timeLineHeightSp <= 44,
        )
        val minAtLowest = timeAxisTypography(
            44f,
            TimetableAxisStyle.periodFontRange.first,
            TimetableAxisStyle.timeFontRange.first,
        )
        assertTrue(minAtLowest.numberSizeSp >= 7)
        assertTrue(minAtLowest.timeSizeSp >= 5)
    }

    @Test
    fun `user base is the ceiling at the tallest cell`() {
        val tallest = timeAxisTypography(68f, periodBaseSp = 13, timeBaseSp = 11)
        assertTrue(tallest.numberSizeSp <= 13)
        assertTrue(tallest.timeSizeSp <= 11)
    }
}
