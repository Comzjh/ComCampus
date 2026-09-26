package com.xmu.course.ui.home

import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.domain.todo.TodoDatePolicy
import com.xmu.course.ui.timetable.TimetableUiState
import com.xmu.course.ui.todo.TodoState
import com.xmu.course.ui.widget.WidgetCourse
import com.xmu.course.ui.widget.WidgetRepository
import java.time.LocalDateTime
import java.time.ZoneId

private const val HOME_URGENT_TODO_LIMIT = 3
private const val HOME_SOON_DAYS = 3L
private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

/** 可注入的时间快照，保证首页派生与文案在测试中可确定。 */
data class HomeClock(val dateTime: LocalDateTime)

data class HomeTodaySnapshot(
    val courses: List<WidgetCourse>,
    val current: WidgetCourse?,
    val next: WidgetCourse?,
    val remaining: List<WidgetCourse>,
    val countdown: String,
    val remainingCount: Int,
)

enum class HomeTodoAvailability {
    LOADING,
    AVAILABLE,
    PARTIAL_ERROR,
}

/** Home 只消费一个纯派生快照；不持久化 current/urgent 等瞬时状态。 */
data class HomeDashboardState(
    val today: HomeTodaySnapshot,
    val urgentTodos: List<TodoFeatureModel>,
    val urgentTodoCount: Int?,
    val todoAvailability: HomeTodoAvailability,
)

fun resolveHomeDashboard(
    timetableState: TimetableUiState,
    todoState: TodoState,
    clock: HomeClock,
    zoneId: ZoneId = ZoneId.systemDefault(),
): HomeDashboardState {
    val week = timetableState.timetable?.let {
        WidgetRepository.computeCurrentWeek(it, clock.dateTime.toLocalDate())
    } ?: timetableState.actualWeek
    val todayCourses = WidgetRepository.filterTodayCourses(
        courses = timetableState.courses,
        skippedIds = timetableState.skippedCourseIds,
        week = week,
        dayOfWeek = clock.dateTime.dayOfWeek.value,
    ).filterNot(WidgetCourse::isSkipped)
    val nowMinute = clock.dateTime.hour * 60 + clock.dateTime.minute
    val current = todayCourses.firstOrNull {
        nowMinute in it.startMinuteOfDay until it.endMinuteOfDay
    }
    val next = todayCourses.firstOrNull { it.startMinuteOfDay > nowMinute }
    val remaining = todayCourses.filter {
        it.endMinuteOfDay > nowMinute && it.id != current?.id && it.id != next?.id
    }
    val focus = current ?: next
    val todaySnapshot = HomeTodaySnapshot(
        courses = todayCourses,
        current = current,
        next = next,
        remaining = remaining,
        countdown = if (current != null) "正在上课" else WidgetRepository.countdownText(nowMinute, focus),
        remainingCount = todayCourses.count { it.endMinuteOfDay > nowMinute },
    )

    val sourceTodos = when (todoState) {
        is TodoState.Success -> todoState.todos
        is TodoState.Error -> todoState.todos
        TodoState.Loading,
        is TodoState.Empty,
        -> emptyList()
    }
    val nowMillis = clock.dateTime.atZone(zoneId).toInstant().toEpochMilli()
    val horizon = nowMillis + HOME_SOON_DAYS * DAY_MILLIS
    fun effectiveDeadline(todo: TodoFeatureModel): Long? = todo.deadline?.let {
        TodoDatePolicy.effectiveDeadline(
            deadlineMillis = it,
            zoneId = zoneId,
            isDateOnly = todo.source == TodoFeatureSource.LOCAL,
        )
    }
    val urgent = sourceTodos
        .asSequence()
        .filterNot(TodoFeatureModel::completed)
        .filter { todo -> effectiveDeadline(todo)?.let { it <= horizon } == true }
        .sortedBy(::effectiveDeadline)
        .toList()

    return HomeDashboardState(
        today = todaySnapshot,
        urgentTodos = urgent.take(HOME_URGENT_TODO_LIMIT),
        urgentTodoCount = if (todoState is TodoState.Success) urgent.size else null,
        todoAvailability = when (todoState) {
            TodoState.Loading -> HomeTodoAvailability.LOADING
            is TodoState.Error -> HomeTodoAvailability.PARTIAL_ERROR
            is TodoState.Empty,
            is TodoState.Success,
            -> HomeTodoAvailability.AVAILABLE
        },
    )
}

fun homeDateLabel(clock: HomeClock): String {
    val weekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val date = clock.dateTime
    return "${date.monthValue}月${date.dayOfMonth}日 ${weekdays[date.dayOfWeek.value - 1]}"
}
