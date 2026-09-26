package com.xmu.course.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.domain.todo.TodoDeadlinePolicy
import com.xmu.course.domain.todo.TodoDatePolicy
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppLargeTitleHeader
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.timetable.TimetableUiState
import com.xmu.course.ui.timetable.TimetableViewModel
import com.xmu.course.ui.todo.TodoViewModel
import com.xmu.course.ui.widget.WidgetCourse
import com.xmu.course.ui.widget.WidgetRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** 首页只回答今天的课程与需要处理的事项，不复制其他主 Tab 的完整内容。 */
@Composable
fun HomeScreen(
    timetableViewModel: TimetableViewModel,
    todoViewModel: TodoViewModel,
    onOpenTimetable: () -> Unit,
    onOpenTodo: () -> Unit,
    onAddTodo: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    clock: HomeClock = HomeClock(LocalDateTime.now()),
) {
    val timetableState by timetableViewModel.uiState.collectAsState()
    val todoState by todoViewModel.state.collectAsState()
    val dashboard = remember(timetableState, todoState, clock) {
        resolveHomeDashboard(timetableState, todoState, clock)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.PagePadding)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.SectionGap),
    ) {
        AppLargeTitleHeader(
            title = "今天",
            subtitle = homeDateLabel(clock),
            subtitleModifier = Modifier.testTag("home_date"),
        )

        TodayFocusCard(
            state = timetableState,
            today = dashboard.today,
            onOpenTimetable = onOpenTimetable,
            onOpenImport = onOpenImport,
        )
        UrgentTodoCard(
            dashboard = dashboard,
            nowMillis = clock.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            onOpenTodo = onOpenTodo,
            onAddTodo = onAddTodo,
        )
    }
}

@Composable
private fun TodayFocusCard(
    state: TimetableUiState,
    today: HomeTodaySnapshot,
    onOpenTimetable: () -> Unit,
    onOpenImport: () -> Unit,
) {
    AppSectionCard(
        title = when {
            !state.hasData -> "今日课程"
            today.current != null -> "现在"
            else -> "下一节"
        },
        action = if (state.hasData) {
            {
                TextButton(
                    onClick = onOpenTimetable,
                    modifier = Modifier.testTag("home_open_timetable"),
                ) { Text("查看课表") }
            }
        } else {
            null
        },
    ) {
        val focus = today.current ?: today.next
        when {
            state.isLoading -> Text(
                "正在加载课表…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !state.hasData -> AppEmptyState(
                title = "还没有课表数据",
                description = "导入课表后可在这里查看今日安排",
                actionLabel = "导入课表",
                onAction = onOpenImport,
                modifier = Modifier.testTag("home_today_empty"),
                compact = true,
                primaryAction = true,
            )
            focus != null -> {
                FocusCourseRow(
                    course = focus,
                    status = today.countdown,
                    tag = if (today.current != null) "home_current_class" else "home_next_class",
                )
                if (today.current != null && today.next != null) {
                    SupportingCourseRow("下一节", today.next, Modifier.testTag("home_next_class"))
                }
                today.remaining.forEach { course -> SupportingCourseRow(null, course) }
                Text(
                    "今天还有 ${today.remainingCount} 节安排",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.Xs),
                )
            }
            today.courses.isEmpty() -> Text(
                "今天没有课程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home_today_empty"),
            )
            else -> Text(
                "今天课程已结束",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home_today_finished"),
            )
        }
    }
}

@Composable
private fun FocusCourseRow(course: WidgetCourse, status: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(course.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(
                homeTimeRangeLabel(course),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppStatusChip(label = status)
    }
}

@Composable
private fun SupportingCourseRow(label: String?, course: WidgetCourse, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(course.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Text(
            homeTimeRangeLabel(course),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UrgentTodoCard(
    dashboard: HomeDashboardState,
    nowMillis: Long,
    onOpenTodo: () -> Unit,
    onAddTodo: () -> Unit,
) {
    AppSectionCard(
        title = "需要处理",
        action = {
            TextButton(onClick = onAddTodo, modifier = Modifier.testTag("home_add_todo")) {
                Text("添加待办")
            }
        },
    ) {
        when {
            dashboard.todoAvailability == HomeTodoAvailability.LOADING -> Text(
                "正在加载待办…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dashboard.urgentTodos.isEmpty() && dashboard.todoAvailability == HomeTodoAvailability.PARTIAL_ERROR -> Text(
                "待办暂时不可用",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("home_todo_error"),
            )
            dashboard.urgentTodos.isEmpty() -> Text(
                "今天没有需要处理的事项",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home_todo_empty"),
            )
            else -> {
                dashboard.urgentTodoCount?.let { count ->
                    Text(
                        "$count 项需要注意",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("home_todo_count"),
                    )
                }
                dashboard.urgentTodos.forEach { todo -> TodoPreviewRow(todo, nowMillis) }
                if (dashboard.todoAvailability == HomeTodoAvailability.PARTIAL_ERROR) {
                    Text(
                        "待办更新失败，正在显示已有数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        TextButton(
            onClick = onOpenTodo,
            modifier = Modifier.fillMaxWidth().testTag("home_open_todo"),
        ) { Text("查看全部待办") }
    }
}

@Composable
private fun TodoPreviewRow(todo: TodoFeatureModel, nowMillis: Long) {
    val deadline = todo.deadline?.let {
        TodoDatePolicy.effectiveDeadline(
            deadlineMillis = it,
            isDateOnly = todo.source == TodoFeatureSource.LOCAL,
        )
    }
    val isDateOnly = todo.source == TodoFeatureSource.LOCAL &&
        todo.deadline?.let(TodoDatePolicy::isDateOnlyValue) == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
    ) {
        Text(todo.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            WidgetRepository.formatTodoDeadline(deadline, nowMillis, isDateOnly = isDateOnly),
            style = MaterialTheme.typography.bodySmall,
            color = if (TodoDeadlinePolicy.isOverdue(deadline, nowMillis)) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun homeTimeRangeLabel(course: WidgetCourse): String {
    val end = String.format(Locale.US, "%02d:%02d", course.endMinuteOfDay / 60, course.endMinuteOfDay % 60)
    val time = "${course.startTime}–$end"
    return if (course.location.isBlank()) time else "$time · ${course.location}"
}
