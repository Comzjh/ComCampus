package com.xmu.course.ui.timetable

import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.ui.home.HomeClock
import com.xmu.course.ui.home.resolveHomeDashboard
import com.xmu.course.ui.todo.TodoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The browsing week may be clamped, but "today" must still be inside the semester.
 * These cases use the default 25-week length and never touch device/user data.
 */
class TimetableSemesterBoundaryAuditTest {
    private val timetable = Timetable(
        id = 1L,
        name = "Synthetic semester",
        semesterId = 1L,
        startDate = "2026-09-14",
        totalWeeks = 25,
    )

    @Test
    fun mondayBeforeSemesterDoesNotReuseFirstWeekAsToday() {
        val snapshot = today("2026-09-07", course(week = 1))

        assertTrue("The semester has not started; today's courses must be empty", snapshot.courses.isEmpty())
        assertNull("A future first-week course must not be today's next class", snapshot.next)
    }

    @Test
    fun mondayAfterSemesterDoesNotRepeatLastWeekAsToday() {
        // Week 25 starts on 2027-03-01; 2027-03-08 is already week 26.
        val snapshot = today("2027-03-08", course(week = 25))

        assertTrue("The semester has ended; today's courses must be empty", snapshot.courses.isEmpty())
        assertNull("A last-week course must not repeat after the semester", snapshot.next)
    }

    @Test
    fun firstMondayStillShowsItsScheduledCourse() {
        val snapshot = today("2026-09-14", course(week = 1))

        assertEquals("First-week course remains available on its real date", listOf(7L), snapshot.courses.map { it.id })
        assertEquals("The next class is the scheduled course", 7L, snapshot.next?.id)
    }

    @Test
    fun lastMondayStillShowsItsScheduledCourse() {
        val snapshot = today("2027-03-01", course(week = 25))

        assertEquals("Last-week course remains available inside the semester", listOf(7L), snapshot.courses.map { it.id })
        assertEquals("The next class is the scheduled course", 7L, snapshot.next?.id)
    }

    private fun today(date: String, course: Course) = resolveHomeDashboard(
        timetableState = TimetableUiState(
            timetable = timetable,
            courses = listOf(course),
            hasData = true,
            isLoading = false,
        ),
        todoState = TodoState.Empty(),
        clock = HomeClock(LocalDate.parse(date).atTime(7, 30)),
        zoneId = ZoneId.of("Asia/Shanghai"),
    ).today

    private fun course(week: Int) = Course(
        id = 7L,
        semesterId = timetable.semesterId,
        name = "Synthetic Monday class",
        dayOfWeek = 1,
        startSection = 1,
        duration = 2,
        weeks = setOf(week),
    )
}
