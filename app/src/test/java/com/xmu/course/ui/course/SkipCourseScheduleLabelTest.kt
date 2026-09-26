package com.xmu.course.ui.course

import com.xmu.course.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipCourseScheduleLabelTest {
    @Test
    fun `includes weekday section range and separated week pattern`() {
        val course = Course(
            name = "课程",
            dayOfWeek = 1,
            startSection = 5,
            duration = 2,
            weeks = setOf(5, 1, 3),
        )

        assertEquals("周一 · 第5-6节 · 1,3,5周", skipCourseScheduleLabel(course))
    }

    @Test
    fun `formats one section and a continuous week range`() {
        val course = Course(
            name = "课程",
            dayOfWeek = 7,
            startSection = 2,
            duration = 1,
            weeks = (1..16).toSet(),
        )

        assertEquals("周日 · 第2节 · 1-16周", skipCourseScheduleLabel(course))
    }
}
