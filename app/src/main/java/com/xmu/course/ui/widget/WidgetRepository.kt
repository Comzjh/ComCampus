package com.xmu.course.ui.widget

import android.content.Context
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.toDomain
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.domain.Course
import com.xmu.course.domain.occursInWeek
import com.xmu.course.domain.todo.TodoDatePolicy
import com.xmu.course.domain.Timetable
import com.xmu.course.ui.timetable.TimeTableConfig
import com.xmu.course.ui.timetable.TimetableCalendar
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** Widget 展示用待办，避免把 Room Entity 直接带入 Glance 内容层。 */
data class WidgetTodoItem(
    val id: Long,
    val title: String,
    val courseName: String?,
    val deadline: Long?,
    val deadlineText: String,
    val isOverdue: Boolean,
    val countdownText: String?,
    val urgency: TodoUrgency,
)

/** Widget 待办快照；totalUnfinished 是全部未完成数量，items 最多展示 4 条。 */
data class WidgetTodoData(
    val totalUnfinished: Int,
    val items: List<WidgetTodoItem>,
)

/** Widget 排序/筛选所需的最小待办输入，不暴露 Room Entity。 */
data class WidgetTodoRecord(
    val id: Long,
    val title: String,
    val courseId: Long?,
    val source: WidgetTodoSource,
    val deadline: Long?,
    val completed: Boolean,
    val updatedTime: Long,
)

enum class WidgetTodoSource {
    LOCAL,
    TRONCLASS,
    UNKNOWN,
}

private fun WidgetTodoRecord.effectiveDeadline(zoneId: ZoneId): Long? =
    deadline?.let {
        TodoDatePolicy.effectiveDeadline(
            deadlineMillis = it,
            zoneId = zoneId,
            isDateOnly = source == WidgetTodoSource.LOCAL,
        )
    }

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
            timetableName = timetable?.name ?: "ComCampus",
            week = week,
            dayOfWeek = day,
            courses = filterTodayCourses(courses, skipped, week, day),
            nowMinuteOfDay = now.hour * 60 + now.minute,
        )
    }

    suspend fun loadTodos(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        db: AppDatabase = AppDatabase.getInstance(context),
    ): WidgetTodoData {
        val todos = db.todoDao().getAll().map(TodoEntity::toWidgetTodoRecord)
        val localCourseNames = db.courseDao().getAll().associate { it.id to it.name }
        val tronCourseNames = db.tronCourseDao().getAll().associate { it.tronCourseId to it.name }
        val selected = selectTodosForWidget(todos, nowMillis, zoneId = zoneId)
        val todosById = todos.associateBy { it.id }

        return selected.copy(
            items = selected.items.map { item ->
                val todo = todosById[item.id]
                item.copy(courseName = todo?.let { courseNameFor(it, localCourseNames, tronCourseNames) })
            },
        )
    }

    /** 复用 Todo 页面相同的排序语义，只在展示层排除已完成项并截取前几条。 */
    fun selectTodosForWidget(
        todos: List<WidgetTodoRecord>,
        nowMillis: Long,
        maxDisplay: Int = 4,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): WidgetTodoData {
        require(maxDisplay >= 0) { "maxDisplay must not be negative" }
        val unfinished = todos.sortedWith(
            compareBy<WidgetTodoRecord> { it.completed }
                .thenBy { it.effectiveDeadline(zoneId) == null }
                .thenBy { it.effectiveDeadline(zoneId) ?: Long.MAX_VALUE }
                .thenByDescending { it.updatedTime },
        ).filterNot { it.completed }
        return WidgetTodoData(
            totalUnfinished = unfinished.size,
            items = unfinished.take(maxDisplay).map { todo ->
                val effectiveDeadline = todo.effectiveDeadline(zoneId)
                val isDateOnly = todo.source == WidgetTodoSource.LOCAL &&
                    todo.deadline?.let(TodoDatePolicy::isDateOnlyValue) == true
                val deadlineDisplay = TodoDeadlineDisplay.resolve(
                    deadlineMillis = effectiveDeadline,
                    nowMillis = nowMillis,
                )
                WidgetTodoItem(
                    id = todo.id,
                    title = todo.title,
                    courseName = null,
                    deadline = effectiveDeadline,
                    deadlineText = formatTodoDeadline(effectiveDeadline, nowMillis, zoneId, isDateOnly),
                    isOverdue = deadlineDisplay.urgency == TodoUrgency.OVERDUE,
                    countdownText = deadlineDisplay.text,
                    urgency = deadlineDisplay.urgency,
                )
            },
        )
    }

    fun formatTodoDeadline(
        deadline: Long?,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        isDateOnly: Boolean = false,
    ): String {
        if (deadline == null) return "无截止时间"
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val target = Instant.ofEpochMilli(deadline).atZone(zoneId)
        if (isDateOnly) return "${target.toLocalDate()}（当天结束）"
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE).format(target)
        return when (target.toLocalDate()) {
            now.toLocalDate() -> "今天 $time"
            now.toLocalDate().plusDays(1) -> "明天 $time"
            else -> DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(target)
        }
    }

    fun truncateWidgetText(value: String, maxChars: Int): String {
        require(maxChars > 0) { "maxChars must be positive" }
        return if (value.length <= maxChars) value else value.take(maxChars - 1) + "…"
    }

    private fun courseNameFor(
        todo: WidgetTodoRecord,
        localCourseNames: Map<Long, String>,
        tronCourseNames: Map<Long, String>,
    ): String? = when (todo.source) {
        WidgetTodoSource.LOCAL -> todo.courseId?.let(localCourseNames::get)
        WidgetTodoSource.TRONCLASS -> todo.courseId?.let(tronCourseNames::get)
        WidgetTodoSource.UNKNOWN -> null
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
        return timetable?.let { TimetableCalendar.currentWeek(it, today) } ?: 1
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

    /** 优先返回正在进行的未跳过课程，否则返回今天下一节未开始课程。 */
    fun findNextCourse(courses: List<WidgetCourse>, nowMinuteOfDay: Int): WidgetCourse? {
        val available = courses.filterNot { it.isSkipped }
        return available.firstOrNull {
            nowMinuteOfDay in it.startMinuteOfDay until it.endMinuteOfDay
        } ?: available.firstOrNull { it.startMinuteOfDay > nowMinuteOfDay }
    }

    fun countdownText(nowMinuteOfDay: Int, next: WidgetCourse?): String {
        next ?: return "今日无课程"
        if (nowMinuteOfDay in next.startMinuteOfDay until next.endMinuteOfDay) return "正在上课"
        val minutes = next.startMinuteOfDay - nowMinuteOfDay
        return when {
            minutes <= 0 -> "马上开始"
            minutes < 60 -> "还有${minutes}分钟"
            else -> "还有${minutes / 60}小时${minutes % 60}分钟"
        }
    }
}

private fun TodoEntity.toWidgetTodoRecord(): WidgetTodoRecord = WidgetTodoRecord(
    id = id,
    title = title,
    courseId = courseId,
    source = when (source) {
        TodoSource.LOCAL.name -> WidgetTodoSource.LOCAL
        TodoSource.TRONCLASS.name -> WidgetTodoSource.TRONCLASS
        else -> WidgetTodoSource.UNKNOWN
    },
    deadline = deadline,
    completed = completed,
    updatedTime = updatedTime,
)
