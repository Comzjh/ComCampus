package com.xmu.course

import com.xmu.course.parser.XmuKingosoftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmuKingosoftParserTest {

    private val parser = XmuKingosoftParser()

    private fun sampleHtml(): String =
        javaClass.getResourceAsStream("/sample_timetable.html")!!
            .readBytes().toString(Charsets.UTF_8)

    @Test fun `解析学期信息`() {
        val result = parser.parse(sampleHtml())
        assertEquals("SYNTHETIC-2026-A", result.semesterCode)
        assertEquals("合成示例学期", result.semesterName)
    }

    @Test fun `解析全部课程记录`() {
        val result = parser.parse(sampleHtml())
        // 合成夹具包含 38 条 arrage 记录，全部应解析成功。
        assertEquals(38, result.courses.size)
    }

    @Test fun `普通课程字段`() {
        val result = parser.parse(sampleHtml())
        val course = result.courses.first { it.name == "合成课程01" && it.weeks == (1..2).toSet() }
        assertEquals(2, course.dayOfWeek)
        assertEquals(1, course.startSection)
        assertEquals(4, course.duration)
        assertEquals("合成教师", course.teacher)
        assertEquals("合成教室 A101", course.location)
        assertEquals(setOf(1, 2), course.weeks)
    }

    @Test fun `单双周与混合周课程`() {
        val result = parser.parse(sampleHtml())
        assertTrue(result.courses.any { it.weeks == (1..15 step 2).toSet() })
        assertTrue(result.courses.any { it.weeks == (2..16 step 2).toSet() })
        assertTrue(
            result.courses.any {
                it.weeks == setOf(1, 3, 6, 8, 9, 11, 13, 15, 16)
            },
        )
    }

    @Test fun `异常HTML不崩溃`() {
        val result = parser.parse("<html><body>broken</body></html>")
        assertTrue(result.courses.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
        val result2 = parser.parse("")
        assertTrue(result2.courses.isEmpty())
    }
}
