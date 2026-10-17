package com.xmu.course.ui.reminder

import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.domain.occursInWeek
import com.xmu.course.ui.timetable.TimeTableConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class CourseReminderOccurrence(
    val timetableId: Long,
    val courseId: Long,
    val courseName: String,
    val location: String,
    val startAtMillis: Long,
    val remindAtMillis: Long,
    val startTime: String,
) {
    /** Includes the occurrence, so next week's class is a distinct reminder. */
    val key: String get() = "$timetableId:$courseId:$startAtMillis"
}

/** Pure semester-wide planning. Missing dates are never inferred from the browsed week. */
object CourseReminderPlanner {
    const val LEAD_TIME_MILLIS = 30 * 60 * 1_000L

    fun occurrences(
        state: TimetableFeatureState,
        zoneId: ZoneId,
        sectionTimes: List<String> = TimeTableConfig.xmuSections,
    ): List<CourseReminderOccurrence> {
        val timetable = state.timetable ?: return emptyList()
        val startDate = timetable.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()
        if (timetable.totalWeeks <= 0) return emptyList()
        val courses = state.courses.filter {
            it.id > 0 && it.semesterId == timetable.semesterId && it.id !in state.skippedCourseIds &&
                it.dayOfWeek in 1..7 && it.duration > 0 && it.startSection > 0 &&
                it.startSection.toLong() + it.duration - 1 <= sectionTimes.size
        }
        return courses.flatMap { course ->
            val start = parseTime(sectionTimes[course.startSection - 1].substringBefore('-'))
                ?: return@flatMap emptyList()
            val end = parseTime(sectionTimes[course.startSection + course.duration - 2].substringAfter('-'))
                ?: return@flatMap emptyList()
            if (!end.isAfter(start)) return@flatMap emptyList()
            course.weeks.asSequence().filter { it in 1..timetable.totalWeeks && course.occursInWeek(it) }
                .mapNotNull { week ->
                    // Match actualWeek + the real weekday even if a manually entered start
                    // date is not Monday. Each teaching week is a seven-day semester window.
                    val date = runCatching {
                        startDate.plusWeeks((week - 1).toLong())
                            .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(course.dayOfWeek)))
                    }
                        .getOrNull() ?: return@mapNotNull null
                    // Nonexistent local times (DST gaps) cannot describe a real class start.
                    val localStart = date.atTime(start)
                    if (zoneId.rules.getValidOffsets(localStart).isEmpty()) return@mapNotNull null
                    val startsAt = localStart.atZone(zoneId).toInstant().toEpochMilli()
                    CourseReminderOccurrence(
                        timetableId = timetable.id,
                        courseId = course.id,
                        courseName = course.name,
                        location = course.location.trim(),
                        startAtMillis = startsAt,
                        remindAtMillis = startsAt - LEAD_TIME_MILLIS,
                        startTime = start.toString(),
                    )
                }.toList()
        }.distinctBy { it.key }.sortedWith(compareBy<CourseReminderOccurrence> { it.remindAtMillis }.thenBy { it.courseId })
    }

    fun next(
        occurrences: List<CourseReminderOccurrence>,
        nowMillis: Long,
        delivered: (String) -> Boolean = { false },
    ): Long? = occurrences.asSequence()
        .filter { it.remindAtMillis >= nowMillis && it.startAtMillis > nowMillis && !delivered(it.key) }
        .minOfOrNull { it.remindAtMillis }

    fun due(
        occurrences: List<CourseReminderOccurrence>,
        scheduledAtMillis: Long,
        nowMillis: Long,
    ): List<CourseReminderOccurrence> = occurrences.filter {
        it.remindAtMillis == scheduledAtMillis && it.remindAtMillis <= nowMillis && it.startAtMillis > nowMillis
    }

    private fun parseTime(value: String): LocalTime? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
