package com.xmu.course

import com.xmu.course.domain.Course
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableLayoutEngineTest {

    private fun course(
        day: Int, start: Int, duration: Int,
        name: String = "C$day-$start", weeks: Set<Int> = (1..16).toSet(),
    ) = Course(name = name, dayOfWeek = day, startSection = start, duration = duration, weeks = weeks)

    @Test fun `普通课程单栏`() {
        val items = TimetableLayoutEngine.assignLanes(listOf(course(1, 1, 2), course(1, 3, 2)))
        assertTrue(items.all { it.laneCount == 1 })
    }

    @Test fun `冲突课程自动分栏`() {
        // 两门 1-2 节冲突 → 组内 2 栏；3-4 节与它们无重叠 → 独立冲突组占满全天宽度。
        val items = TimetableLayoutEngine.assignLanes(
            listOf(course(1, 1, 2, "A"), course(1, 1, 2, "B"), course(1, 3, 2, "C")),
        )
        assertEquals(2, items.first { it.course.name == "A" }.laneCount)
        assertEquals(2, items.first { it.course.name == "B" }.laneCount)
        // C 是独立冲突组：laneCount=1，宽度不受其他时段冲突影响
        assertEquals(0, items.first { it.course.name == "C" }.laneIndex)
        assertEquals(1, items.first { it.course.name == "C" }.laneCount)
    }

    @Test fun `按周过滤：非本周保留但淡化`() {
        val layout = TimetableLayoutEngine.layoutForWeek(
            listOf(course(2, 1, 2, weeks = setOf(1, 2, 5)), course(3, 1, 2)),
            week = 3,
        )
        // 非本周课程保留在网格中（淡化显示），当前周课程正常显示
        assertTrue(layout.getValue(2).single().let { !it.isCurrentWeek })
        assertTrue(layout.getValue(3).single().isCurrentWeek)
    }

    @Test fun `多星期课程分天布局`() {
        val layout = TimetableLayoutEngine.layoutForWeek(
            listOf(course(1, 1, 2), course(2, 3, 2), course(4, 5, 2)),
            week = 1,
        )
        assertEquals(setOf(1, 2, 4), layout.keys)
        layout.values.forEach { day -> assertTrue(day.all { it.laneCount == 1 }) }
    }

    @Test fun `跨多节课程与相邻不冲突`() {
        val items = TimetableLayoutEngine.assignLanes(
            listOf(course(5, 1, 4, "长课"), course(5, 5, 2, "后续")),
        )
        assertTrue(items.all { it.laneCount == 1 })
    }
}
