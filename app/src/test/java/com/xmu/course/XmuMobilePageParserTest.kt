package com.xmu.course

import com.xmu.course.parser.XmuKingosoftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 以真实手机端金智页面（MHT 导出，已脱敏）作为回归夹具。
 * 结构：#jsTbl_01 + td[jc][xq] + div.arrage（部分记录无教室行，仅 3 个子 div）。
 */
class XmuMobilePageParserTest {

    private val parser = XmuKingosoftParser()

    private fun mobileHtml(): String =
        javaClass.getResourceAsStream("/sample_mobile.html")!!
            .readBytes().toString(Charsets.UTF_8)

    @Test fun `手机端页面解析出全部课程`() {
        val result = parser.parse(mobileHtml())
        assertEquals("20261", result.semesterCode)
        assertEquals(38, result.courses.size)
        assertTrue("不应有 warning: ${result.warnings}", result.warnings.isEmpty())
    }

    @Test fun `课程字段完整（含无教室记录）`() {
        val result = parser.parse(mobileHtml())
        val physics = result.courses.first { it.name.startsWith("大学物理实验") && it.weeks == (1..2).toSet() }
        assertEquals(2, physics.dayOfWeek)   // 周二
        assertEquals(1, physics.startSection)
        assertEquals(4, physics.duration)
        assertEquals("陈婷", physics.teacher)
        assertEquals("海韵教学楼104", physics.location)
        // 手机端 3 子节点记录：教室为空但教师必须存在
        val chem = result.courses.first { it.name.startsWith("基础化学实验") }
        assertTrue(chem.teacher.isNotBlank())
    }

    @Test fun `周次覆盖单双周与混合列表`() {
        val result = parser.parse(mobileHtml())
        assertTrue(result.courses.any { it.weeks == (1..15 step 2).toSet() })
        assertTrue(result.courses.any { it.weeks == (2..16 step 2).toSet() })
        assertTrue(result.courses.any { it.weeks == setOf(1, 3, 6, 8, 9, 11, 13, 15, 16) })
    }
}
