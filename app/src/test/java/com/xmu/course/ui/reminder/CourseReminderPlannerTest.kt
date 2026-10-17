package com.xmu.course.ui.reminder

import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReminderPlannerTest {
    @Test
    fun manuallyEnteredNonMondayStartStillUsesTheCoursesRealWeekdayWithinTheSemester() {
        val state = reminderState().let {
            it.copy(timetable = it.timetable!!.copy(startDate = "2026-10-14", totalWeeks = 1),
                courses = listOf(it.courses.single().copy(dayOfWeek = 1, weeks = setOf(1))))
        }
        val planned = CourseReminderPlanner.occurrences(state, ZoneId.of("Asia/Shanghai"))
        assertEquals(1, planned.size)
        assertEquals(reminderMillis("2026-10-19T07:30"), planned.single().remindAtMillis)
    }

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun reminderIsExactlyThirtyMinutesBeforeSharedSectionTime() {
        val occurrence = plan().first()
        assertEquals(reminderMillis("2026-10-12T08:00"), occurrence.startAtMillis)
        assertEquals(reminderMillis("2026-10-12T07:30"), occurrence.remindAtMillis)
        assertEquals("08:00", occurrence.startTime)
        assertEquals("教学楼 101", occurrence.location)
    }

    @Test
    fun injectedSectionTimesControlTheStartRatherThanHardcodedEightOClock() {
        val occurrence = CourseReminderPlanner.occurrences(
            reminderState(), zone, listOf("09:20-10:05"),
        ).first()
        assertEquals(reminderMillis("2026-10-12T08:50"), occurrence.remindAtMillis)
        assertEquals("09:20", occurrence.startTime)
    }

    @Test
    fun futureSemesterAndSparseWeeksDoNotDependOnBrowsedOrStoredWeek() {
        val state = reminderState().let {
            it.copy(timetable = it.timetable!!.copy(currentWeek = 4), courses = listOf(it.courses.single().copy(weeks = setOf(2, 4))))
        }
        val times = plan(state).map { it.remindAtMillis }
        assertEquals(listOf(reminderMillis("2026-10-19T07:30"), reminderMillis("2026-11-02T07:30")), times)
        assertEquals(times.first(), CourseReminderPlanner.next(plan(state), reminderMillis("2026-10-10T12:00")))
    }

    @Test
    fun nextReminderCanBeMoreThanTwentyFourHoursOrAWeekAway() {
        val state = reminderState().let { it.copy(courses = listOf(it.courses.single().copy(weeks = setOf(4)))) }
        assertEquals(
            reminderMillis("2026-11-02T07:30"),
            CourseReminderPlanner.next(plan(state), reminderMillis("2026-10-12T09:00")),
        )
    }

    @Test
    fun hiddenWeekendColumnsDoNotCancelActualWeekendClasses() {
        val state = reminderState().let { it.copy(courses = listOf(it.courses.single().copy(dayOfWeek = 7, weeks = setOf(1)))) }
        assertFalse(state.config.showSunday)
        assertEquals(reminderMillis("2026-10-18T07:30"), plan(state).single().remindAtMillis)
    }

    @Test
    fun skippedAndOtherSemesterCoursesAreExcluded() {
        val state = reminderState().let {
            it.copy(
                courses = it.courses + it.courses.single().copy(id = 2L, semesterId = 99L),
                skippedCourseIds = setOf(1L),
            )
        }
        assertTrue(plan(state).isEmpty())
    }

    @Test
    fun missingOrInvalidSemesterDatesNeverInventAnOccurrence() {
        assertTrue(plan(reminderState().copy(timetable = null)).isEmpty())
        for (date in listOf(null, "", "not-a-date", "2026-02-30")) {
            val state = reminderState().let { it.copy(timetable = it.timetable!!.copy(startDate = date)) }
            assertTrue(plan(state).isEmpty())
        }
    }

    @Test
    fun semesterBoundsExcludeInvalidWeeksAndDoNotClampPastDatesIntoLastWeek() {
        val state = reminderState().let { it.copy(courses = listOf(it.courses.single().copy(weeks = setOf(-1, 0, 1, 4, 5)))) }
        assertEquals(2, plan(state).size)
        assertNull(CourseReminderPlanner.next(plan(state), reminderMillis("2026-11-09T00:00")))
        assertTrue(plan(state.copy(timetable = state.timetable!!.copy(totalWeeks = 0))).isEmpty())
    }

    @Test
    fun invalidDaySectionDurationAndMalformedTimesAreRejected() {
        val state = reminderState()
        val course = state.courses.single()
        for (invalid in listOf(
            course.copy(dayOfWeek = 0), course.copy(dayOfWeek = 8),
            course.copy(startSection = 0), course.copy(startSection = 12),
            course.copy(duration = 0), course.copy(duration = Int.MAX_VALUE),
        )) assertTrue(plan(state.copy(courses = listOf(invalid))).isEmpty())
        for (time in listOf("25:00-26:00", "08:99-09:00", "08:00-07:00", "broken")) {
            assertTrue(CourseReminderPlanner.occurrences(state, zone, listOf(time)).isEmpty())
        }
    }

    @Test
    fun earlyMorningClassCanRemindOnThePreviousCalendarDay() {
        val occurrence = CourseReminderPlanner.occurrences(reminderState(), zone, listOf("00:15-01:00")).first()
        assertEquals(reminderMillis("2026-10-11T23:45"), occurrence.remindAtMillis)
    }

    @Test
    fun sameTimeClassesAllAppearAndWeeklyOccurrencesHaveDifferentKeys() {
        val state = reminderState().let { it.copy(courses = it.courses + it.courses.single().copy(id = 2L, name = "另一门课")) }
        val occurrences = plan(state)
        val due = CourseReminderPlanner.due(occurrences, occurrences.first().remindAtMillis, occurrences.first().remindAtMillis)
        assertEquals(listOf(1L, 2L), due.map { it.courseId })
        assertEquals(8, occurrences.map { it.key }.toSet().size)
    }

    @Test
    fun duplicateRecordsDoNotDuplicateAnOccurrence() {
        val state = reminderState().let { it.copy(courses = it.courses + it.courses) }
        assertEquals(4, plan(state).size)
    }

    @Test
    fun dueRequiresMatchingAlarmAndNeverSendsEarlyOrAfterClassBegins() {
        val occurrence = plan().first()
        assertTrue(CourseReminderPlanner.due(listOf(occurrence), occurrence.remindAtMillis, occurrence.remindAtMillis - 1).isEmpty())
        assertTrue(CourseReminderPlanner.due(listOf(occurrence), occurrence.remindAtMillis - 1, occurrence.remindAtMillis).isEmpty())
        assertEquals(1, CourseReminderPlanner.due(listOf(occurrence), occurrence.remindAtMillis, occurrence.startAtMillis - 1).size)
        assertTrue(CourseReminderPlanner.due(listOf(occurrence), occurrence.remindAtMillis, occurrence.startAtMillis).isEmpty())
    }

    @Test
    fun nextExcludesPassedReminderTimesAndAlreadyDeliveredOccurrences() {
        val occurrences = plan()
        assertEquals(occurrences[1].remindAtMillis, CourseReminderPlanner.next(occurrences, occurrences[0].remindAtMillis + 1))
        assertEquals(occurrences[1].remindAtMillis, CourseReminderPlanner.next(occurrences, occurrences[0].remindAtMillis) { it == occurrences[0].key })
    }

    @Test
    fun timezoneIsAppliedToTheActualOccurrenceAndNonexistentLocalStartsAreSkipped() {
        assertEquals(
            60 * 60 * 1_000L,
            plan().first().startAtMillis - CourseReminderPlanner.occurrences(reminderState(), ZoneId.of("Asia/Tokyo")).first().startAtMillis,
        )
        val state = reminderState().let {
            it.copy(timetable = it.timetable!!.copy(startDate = "2026-03-02", totalWeeks = 1),
                courses = listOf(it.courses.single().copy(dayOfWeek = 7, weeks = setOf(1))))
        }
        assertTrue(CourseReminderPlanner.occurrences(state, ZoneId.of("America/New_York"), listOf("02:30-03:15")).isEmpty())
    }

    private fun plan(state: TimetableFeatureState = reminderState()) = CourseReminderPlanner.occurrences(state, zone)
}

internal fun reminderMillis(local: String, zone: ZoneId = ZoneId.of("Asia/Shanghai")): Long =
    LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

internal fun reminderState(): TimetableFeatureState = TimetableFeatureState(
    timetable = Timetable(id = 1L, name = "合成课表", semesterId = 10L, startDate = "2026-10-12", totalWeeks = 4),
    courses = listOf(Course(id = 1L, semesterId = 10L, name = "测试课程", location = " 教学楼 101 ",
        dayOfWeek = 1, startSection = 1, duration = 1, weeks = setOf(1, 2, 3, 4))),
)
