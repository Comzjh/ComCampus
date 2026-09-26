package com.xmu.course.data.tronclass.matcher

import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseMatcherTest {
    @Test
    fun `完全匹配优先返回匹配课程`() {
        val result = CourseMatcher.match(local("Python程序设计"), listOf(tron("Python程序设计")))

        assertEquals(MatchStrategy.ExactName, result?.strategy)
        assertEquals(1L, result?.tronCourse?.tronCourseId)
    }

    @Test
    fun `括号班号差异通过标准化匹配`() {
        val result = CourseMatcher.match(local("Python程序设计（11）"), listOf(tron("Python程序设计")))

        assertEquals(MatchStrategy.NormalizedName, result?.strategy)
    }

    @Test
    fun `空格和中文班号通过标准化匹配`() {
        val result = CourseMatcher.match(local("高等 数学 第1班"), listOf(tron("高等数学")))

        assertEquals(MatchStrategy.NormalizedName, result?.strategy)
    }

    @Test
    fun `无法唯一确定时不绑定`() {
        val result = CourseMatcher.match(
            local("物理"),
            listOf(tron("大学物理", 1L), tron("物理实验", 2L)),
        )

        assertNull(result)
    }

    @Test
    fun `模糊匹配只接受唯一候选`() {
        val result = CourseMatcher.match(local("大学英语听说"), listOf(tron("大学英语")))

        assertEquals(MatchStrategy.FuzzyName, result?.strategy)
    }

    private fun local(name: String) = Course(
        id = 10L,
        name = name,
        dayOfWeek = 1,
        startSection = 1,
        duration = 2,
        weeks = setOf(1),
    )

    private fun tron(name: String, id: Long = 1L) = TronCourseEntity(
        tronCourseId = id,
        name = name,
        semester = "2026-2027秋季",
        instructor = "测试教师",
        updatedTime = 1L,
    )
}
