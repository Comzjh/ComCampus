package com.xmu.course

import com.xmu.course.domain.Course
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutBugRegressionTest {

    private fun course(
        name: String,
        teacher: String = "陈婷",
        location: String = "海韵教学楼104",
        day: Int = 4,
        start: Int = 5,
        duration: Int = 4,
    ) = Course(
        name = name,
        teacher = teacher,
        location = location,
        dayOfWeek = day,
        startSection = start,
        duration = duration,
        weeks = (1..16).toSet(),
    )

    @Test
    fun sameTimeTwoCoursesRenderBoth() {
        val layout = TimetableLayoutEngine.layoutForWeek(
            listOf(course("大学物理实验(08)"), course("基础化学实验（二）(04)")),
            week = 1,
        )
        val items = layout.getValue(4)
        assertEquals(2, items.size)
        assertEquals(setOf(0, 1), items.map { it.laneIndex }.toSet())
        assertEquals(listOf(2, 2), items.map { it.laneCount })
        assertEquals(listOf(4, 4), items.map { it.course.duration })
    }

    @Test
    fun sameNameDifferentTeacherNotMerge() {
        val merged = TimetableLayoutEngine.mergeSameCourse(
            listOf(
                course("高等数学A", teacher = "教师甲"),
                course("高等数学A", teacher = "教师乙"),
            ),
        )
        assertEquals(2, merged.size)
    }
}
