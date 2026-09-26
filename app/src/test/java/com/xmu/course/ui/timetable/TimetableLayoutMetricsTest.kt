package com.xmu.course.ui.timetable

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableLayoutMetricsTest {
    @Test
    fun `小屏使用更紧凑的时间轴和节次高度`() {
        val small = calculateTimetableLayoutMetrics(300.dp, 52.dp)
        val baseline = calculateTimetableLayoutMetrics(360.dp, 52.dp)

        assertTrue(small.timeAxisWidth < baseline.timeAxisWidth)
        assertTrue(small.cellHeight < baseline.cellHeight)
        assertTrue(small.headerHeight < baseline.headerHeight)
        assertTrue(small.cellHeight >= 44.dp)
    }

    @Test
    fun `大屏缩放有上限避免课程卡片过度膨胀`() {
        val large = calculateTimetableLayoutMetrics(600.dp, 52.dp)

        assertTrue(large.timeAxisWidth <= 68.dp)
        assertTrue(large.cellHeight <= 68.dp)
        assertTrue(large.headerHeight <= 52.dp)
    }

    @Test
    fun `星期栏与日期栏超出表头容量时按比例共同回缩`() {
        // 46dp 表头容量 = 29：1.25×12 + 12 = 27 ≤ 29，默认值原样保留。
        assertTrue(fitDateHeaderFonts(46f, 12, 12) == (12 to 12))
        // 最矮 42dp 表头容量 = 25：默认 12/12（需 27）按比例回缩到 11/11。
        assertTrue(fitDateHeaderFonts(42f, 12, 12) == (11 to 11))
        // 两栏独立：星期 10 + 日期 16（需 28.5 ≤ 35）不触发回缩。
        assertTrue(fitDateHeaderFonts(52f, 10, 16) == (10 to 16))
        // 16/16（需 36 > 35）按比例回缩但仍高于下限。
        assertTrue(fitDateHeaderFonts(52f, 16, 16) == (15 to 15))
        // 极端小表头也保持 9sp 可读下限，且不超过基准。
        assertTrue(fitDateHeaderFonts(30f, 16, 16) == (9 to 9))
    }

    @Test
    fun systemFontScaleShrinksWeekdayRowWithoutBreakingFloor() {
        // fontScale 1.0 matches the original behavior.
        assertTrue(fitDateHeaderFonts(46f, 12, 12, 1f) == (12 to 12))
        // fontScale 1.3: needed = 1.25*12*1.3 + 12 = 31.5 > 29 -> factor ~0.9206 -> 11/11.
        assertTrue(fitDateHeaderFonts(46f, 12, 12, 1.3f) == (11 to 11))
        // fontScale below 1 does not enlarge; clamped to 1.0.
        assertTrue(fitDateHeaderFonts(46f, 12, 12, 0.8f) == (12 to 12))
        // Extreme scale still keeps the 9sp readability floor.
        assertTrue(fitDateHeaderFonts(30f, 12, 12, 1.3f) == (9 to 9))
    }
}
