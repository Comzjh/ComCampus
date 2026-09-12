package com.xmu.course.ui.widget

import android.content.Context
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.toDomain
import com.xmu.course.domain.Course
import com.xmu.course.domain.occursInWeek
import com.xmu.course.domain.Timetable
import com.xmu.course.ui.timetable.TimeTableConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Widget 展示用课程快照。 */
data class WidgetCourse(
    val id: Long,
    val name: String,
    val location: String,
    val startSection: Int,
    val startTime: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isSkipped: Boolean,
)

/** Widget 展示快照。 */
data class WidgetTimetableData(
    val timetableName: String,
    val week: Int,
    val dayOfWeek: Int,
    val courses: List<WidgetCourse>,
    val nowMinuteOfDay: Int,
)

/**
 * Widget 数据仓库：只读取 Room/Prefs，不修改课程结构。
 * 纯函数便于 JVM 单测；网络/导入/课表布局引擎均不感知 Widget。
 */
object WidgetRepository {

    suspend fun loadToday(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
        db: AppDatabase = AppDatabase.getInstance(context),
    ): WidgetTimetableData {
        TimetablePrefs.load(context)
        val prefsId = TimetablePrefs.currentTimetableId.value
        val timetable = prefsId?.let { db.timetableDao().getById(it)?.toDomain() }
            ?: db.timetableDao().getAll().firstOrNull()?.toDomain()

        val courses = timetable?.let { tt ->
            db.courseDao().getBySemester(tt.semesterId).map { it.toDomain() }
        }.orEmpty()
        val skipped = db.skippedCourseDao().getSkippedCourseIds().toSet()
        val week = computeCurrentWeek(timetable, today)
        val day = today.dayOfWeek.value

        return WidgetTimetableData(
            timetableName = timetable?.name ?: "XMU Course",
            week = week,
            dayOfWeek = day,
            courses = filterTodayCourses(courses, skipped, week, day),
            nowMinuteOfDay = now.hour * 60 + now.minute,
        )
    }

    fun Course.toWidgetCourse(skipped: Set<Long>): WidgetCourse {
        val start = TimeTableConfig.timeOf(startSection).substringBefore('-')
        val end = TimeTableConfig.timeOf(startSection + duration - 1).substringAfter('-')
        return WidgetCourse(
            id = id,
            name = name,
            location = location,
            startSection = startSection,
            startTime = start,
            startMinuteOfDay = parseClockMinute(start),
            endMinuteOfDay = parseClockMinute(end),
            isSkipped = id in skipped,
        )
    }

    fun parseClockMinute(value: String): Int {
        val parts = value.split(':').mapNotNull { it.toIntOrNull() }
        require(parts.size == 2) { "Invalid clock value: $value" }
        return parts[0] * 60 + parts[1]
    }

    /** 与课表页一致：开学日所在周为第 1 周。 */
    fun computeCurrentWeek(timetable: Timetable?, today: LocalDate): Int {
        val start = timetable?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 1
        if (today.isBefore(start)) return 1
        val days = ChronoUnit.DAYS.between(start, today)
        return (days / 7 + 1).toInt()
    }

    fun filterTodayCourses(
        courses: List<Course>,
        skippedIds: Set<Long>,
        week: Int,
        dayOfWeek: Int,
    ): List<WidgetCourse> =
        courses
            .filter { it.dayOfWeek == dayOfWeek && it.occursInWeek(week) }
            .map { it.toWidgetCourse(skippedIds) }
            .sortedBy { it.startMinuteOfDay }

    /** 寻找今天下一节未开始课程。 */
    fun findNextCourse(courses: List<WidgetCourse>, nowMinuteOfDay: Int): WidgetCourse? =
        courses.firstOrNull { it.startMinuteOfDay > nowMinuteOfDay }

    fun countdownText(nowMinuteOfDay: Int, next: WidgetCourse?): String {
        next ?: return "今日无课程"
        val minutes = next.startMinuteOfDay - nowMinuteOfDay
        return when {
            minutes <= 0 -> "马上开始"
            minutes < 60 -> "还有${minutes}分钟"
            else -> "还有${minutes / 60}小时${minutes % 60}分钟"
        }
    }
}
