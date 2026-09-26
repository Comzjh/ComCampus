package com.xmu.course

import com.xmu.course.ui.timetable.TimetableCalendar
import com.xmu.course.ui.timetable.TimetableGridMetrics
import com.xmu.course.ui.timetable.TimetableGridCell
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import com.xmu.course.ui.timetable.hitTestTimetableCell
import com.xmu.course.ui.timetable.occupiedTimetableSections
import com.xmu.course.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimetableInteractionTest {

    @Test
    fun `date calculation handles week day month year and leap day`() {
        assertEquals(
            LocalDate.of(2024, 2, 26),
            TimetableCalendar.dateFor("2024-02-26", week = 1, dayOfWeek = 1),
        )
        assertEquals(
            LocalDate.of(2024, 3, 3),
            TimetableCalendar.dateFor("2024-02-26", week = 1, dayOfWeek = 7),
        )
        assertEquals(
            LocalDate.of(2024, 3, 4),
            TimetableCalendar.dateFor("2024-02-26", week = 2, dayOfWeek = 1),
        )
        assertEquals(
            LocalDate.of(2025, 1, 5),
            TimetableCalendar.dateFor("2024-12-30", week = 1, dayOfWeek = 7),
        )
        assertEquals(
            LocalDate.of(2024, 2, 29),
            TimetableCalendar.dateFor("2024-02-26", week = 1, dayOfWeek = 4),
        )
    }

    @Test
    fun `today highlight compares calculated date not only weekday`() {
        val today = LocalDate.of(2026, 9, 14)
        assertTrue(TimetableCalendar.isToday("2026-09-07", week = 2, dayOfWeek = 1, today))
        assertFalse(TimetableCalendar.isToday("2026-09-07", week = 3, dayOfWeek = 1, today))
        assertFalse(TimetableCalendar.isToday("2026-09-07", week = 2, dayOfWeek = 2, today))
    }

    @Test
    fun `date calculation safely rejects missing or invalid input`() {
        assertNull(TimetableCalendar.dateFor(null, week = 1, dayOfWeek = 1))
        assertNull(TimetableCalendar.dateFor("not-a-date", week = 1, dayOfWeek = 1))
        assertNull(TimetableCalendar.dateFor("2026-09-14", week = 0, dayOfWeek = 1))
        assertNull(TimetableCalendar.dateFor("2026-09-14", week = 1, dayOfWeek = 8))
    }

    @Test
    fun `hit test maps edges and scrolled viewport to cells`() {
        val metrics = TimetableGridMetrics(
            dayColumns = (1..7).toList(),
            columnWidthPx = 100f,
            sectionHeightPx = 50f,
            sectionCount = 11,
        )

        assertEquals(TimetableGridCell(dayOfWeek = 1, startSection = 1), hitTestTimetableCell(0f, 0f, metrics))
        assertEquals(TimetableGridCell(dayOfWeek = 7, startSection = 1), hitTestTimetableCell(99.9f + 600f, 49.9f, metrics))
        assertEquals(TimetableGridCell(dayOfWeek = 3, startSection = 3), hitTestTimetableCell(200f, 0f, metrics.copy(scrollOffsetPx = 100f)))
        assertEquals(TimetableGridCell(dayOfWeek = 1, startSection = 11), hitTestTimetableCell(1f, 549.9f, metrics))
        assertNull(hitTestTimetableCell(700f, 10f, metrics))
        assertNull(hitTestTimetableCell(10f, 550f, metrics))
        assertNull(hitTestTimetableCell(-0.1f, 10f, metrics))
    }

    @Test
    fun `occupied sections follow existing lane layout and duration`() {
        val course = Course(
            name = "跨节课程",
            dayOfWeek = 3,
            startSection = 5,
            duration = 3,
            weeks = (1..16).toSet(),
        )
        val layout = TimetableLayoutEngine.layoutForWeek(listOf(course), week = 1)
        val occupied = occupiedTimetableSections(layout, (1..7).toList(), sectionCount = 11)

        assertFalse(occupied[1][4])
        assertTrue(occupied[2][4])
        assertTrue(occupied[2][5])
        assertTrue(occupied[2][6])
        assertFalse(occupied[2][7])
    }
}
