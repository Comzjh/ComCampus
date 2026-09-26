package com.xmu.course

import com.xmu.course.parser.WeekPatternParser
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPatternParserTest {

    @Test fun `区间全周`() {
        assertEquals((1..16).toSet(), WeekPatternParser.parse("1-16周"))
        assertEquals((3..16).toSet(), WeekPatternParser.parse("3-16周"))
    }

    @Test fun `单双周区间`() {
        assertEquals((1..15 step 2).toSet(), WeekPatternParser.parse("1-15单周"))
        assertEquals((2..16 step 2).toSet(), WeekPatternParser.parse("2-16双周"))
    }

    @Test fun `离散周`() {
        assertEquals(setOf(1, 4, 7), WeekPatternParser.parse("1,4,7"))
    }

    @Test fun `混合列表`() {
        // 样张实测：1-3单周,6,8-9,11-13单周,15-16周
        val expected = setOf(1, 3, 6, 8, 9, 11, 13, 15, 16)
        assertEquals(expected, WeekPatternParser.parse("1-3单周,6,8-9,11-13单周,15-16周"))
    }

    @Test fun `中文逗号与空格容错`() {
        assertEquals(setOf(2, 5, 7), WeekPatternParser.parse("2，5，7 周"))
    }

    @Test fun `异常输入不崩溃`() {
        assertEquals(emptySet<Int>(), WeekPatternParser.parse(""))
        assertEquals(emptySet<Int>(), WeekPatternParser.parse("abc,周,99-1,8-3单周"))
        assertEquals(emptySet<Int>(), WeekPatternParser.parse(",,,"))
    }
}
