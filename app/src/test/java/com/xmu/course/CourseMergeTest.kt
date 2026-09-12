package com.xmu.course

import com.xmu.course.domain.Course
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMergeTest {

    private fun course(
        day: Int, start: Int, duration: Int, weeks: Set<Int>, name: String = "物理",
    ) = Course(name = name, teacher = "陈婷", location = "海韵104", dayOfWeek = day, startSection = start, duration = duration, weeks = weeks)

    @Test fun `同课不同周次记录合并为一张整卡`() {
        // 金智拆分：1-2周 + 3-16周，同为周二 1-4 节
        val merged = TimetableLayoutEngine.mergeSameCourse(
            listOf(course(2, 1, 4, setOf(1, 2)), course(2, 1, 4, (3..16).toSet())),
        )
        assertEquals(1, merged.size)
        assertEquals((1..16).toSet(), merged[0].weeks)
        assertEquals(1, merged[0].startSection)
        assertEquals(4, merged[0].duration)
    }

    @Test fun `节次相邻的同课也合并`() {
        val merged = TimetableLayoutEngine.mergeSameCourse(
            listOf(course(1, 1, 2, (1..8).toSet()), course(1, 3, 2, (9..16).toSet())),
        )
        assertEquals(1, merged.size)
        assertEquals(4, merged[0].duration)
        assertEquals((1..16).toSet(), merged[0].weeks)
    }

    @Test fun `不同课不合并`() {
        val merged = TimetableLayoutEngine.mergeSameCourse(
            listOf(course(2, 1, 2, (1..16).toSet(), "物理"), course(2, 1, 2, (1..16).toSet(), "化学")),
        )
        assertEquals(2, merged.size)
    }

    @Test fun `合并后按周过滤并标记当前周`() {
        val layout = TimetableLayoutEngine.layoutForWeek(
            listOf(course(2, 1, 4, setOf(1, 2)), course(2, 1, 4, (3..16).toSet())),
            week = 1,
        )
        val items = layout[2]!!
        // 本周记录 1 张 active 卡（1-2 周），非本周记录（3-16 周）作为独立 ghost 淡化显示
        val active = items.filter { it.isCurrentWeek }
        val ghost = items.filter { !it.isCurrentWeek }
        assertEquals(1, active.size)
        assertEquals((1..2).toSet(), active[0].course.weeks)
        assertEquals(4, active[0].course.duration)
        assertEquals(1, ghost.size)
        assertEquals((3..16).toSet(), ghost[0].course.weeks)
    }

    @Test fun `非本周课程带淡化标记`() {
        val layout = TimetableLayoutEngine.layoutForWeek(
            listOf(course(3, 3, 2, setOf(5, 6), "期末课")),
            week = 1,
        )
        val items = layout[3]!!
        assertEquals(1, items.size)
        assertFalse(items[0].isCurrentWeek)
    }
}
