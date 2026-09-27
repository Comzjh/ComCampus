package com.xmu.course.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.style.TextOverflow
import com.xmu.course.ui.components.AppListDivider
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.AppSpacing
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.ui.components.AppLargeTitleHeader
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppLoadingState
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.domain.todo.TodoDeadlinePolicy
import com.xmu.course.domain.todo.TodoDatePolicy
import com.xmu.course.contracts.todo.model.featureSource
import com.xmu.course.ui.widget.TodoDeadlineDisplay
import com.xmu.course.ui.widget.TodoUrgency
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.delay

private enum class TodoBucket(val label: String) {
    ALL("全部"),
    OVERDUE("逾期"),
    ONE_DAY("1 天"),
    THREE_DAYS("3 天"),
    SEVEN_DAYS("7 天"),
    LONG_TERM("长期"),
}

data class TodoDailySection(
    val label: String,
    val todos: List<TodoFeatureModel>,
)

private fun TodoFeatureModel.effectiveDeadline(zoneId: ZoneId = ZoneId.systemDefault()): Long? =
    deadline?.let { value ->
        TodoDatePolicy.effectiveDeadline(
            deadlineMillis = value,
            zoneId = zoneId,
            isDateOnly = source == TodoFeatureSource.LOCAL,
        )
    }

/** Todo 首页范围的日常分组；只从 deadline/completed 派生，不改变持久化语义。 */
fun groupTodosForDailyView(
    todos: List<TodoFeatureModel>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<TodoDailySection> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val buckets = linkedMapOf(
        "逾期" to mutableListOf<TodoFeatureModel>(),
        "今天" to mutableListOf(),
        "近期" to mutableListOf(),
        "以后" to mutableListOf(),
        "无截止日期" to mutableListOf(),
    )
    todos.filterNot { it.completed }
        .sortedWith(
            compareBy<TodoFeatureModel> { it.effectiveDeadline(zoneId) == null }
                .thenBy { it.effectiveDeadline(zoneId) ?: Long.MAX_VALUE },
        )
        .forEach { todo ->
            val deadline = todo.effectiveDeadline(zoneId)
            val label = when {
                deadline == null -> "无截止日期"
                TodoDeadlinePolicy.isOverdue(deadline, nowMillis) -> "逾期"
                Instant.ofEpochMilli(deadline).atZone(zoneId).toLocalDate() == today -> "今天"
                !Instant.ofEpochMilli(deadline).atZone(zoneId).toLocalDate().isAfter(today.plusDays(7)) -> "近期"
                else -> "以后"
            }
            buckets.getValue(label) += todo
        }
    return buildList {
        buckets.forEach { (label, items) ->
            if (items.isNotEmpty()) add(TodoDailySection(label, items))
        }
        todos.filter { it.completed }.takeIf { it.isNotEmpty() }?.let {
            add(TodoDailySection("已完成", it))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoScreenContent(
    viewModel: TodoViewModel,
    onOpenTronClass: () -> Unit = {},
    openEditorRequest: Boolean = false,
    onOpenEditorRequestConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val autoSyncMessage by viewModel.autoSyncMessage.collectAsState()
    val syncSummary by viewModel.syncSummary.collectAsState()
    var editorTodo by remember { mutableStateOf<TodoFeatureModel?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(openEditorRequest) {
        if (openEditorRequest) {
            editorTodo = null
            showEditor = true
            onOpenEditorRequestConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppLargeTitleHeader(
                title = "待办",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = AppSpacing.Lg, end = AppSpacing.Lg, top = AppSpacing.Lg),
                trailingContent = {
                TutorialToolbarAction()
                IconButton(
                    onClick = viewModel::refreshFromTronClass,
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.heightIn(max = 22.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新畅课待办")
                    }
                }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorTodo = null; showEditor = true },
                modifier = Modifier.tutorialTarget(TutorialTargetKey.TODO_ADD),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增待办")
            }
        },
    ) { innerPadding ->
        when (state) {
            TodoState.Loading -> LoadingContent(innerPadding)
            is TodoState.Empty -> TodoListContent(
                innerPadding = innerPadding,
                todos = emptyList(),
                courseOptions = (state as TodoState.Empty).courseOptions,
                syncSummary = syncSummary,
                syncMessage = autoSyncMessage,
                onToggle = viewModel::setCompleted,
                onEdit = {},
                onDelete = viewModel::deleteTodo,
                onOpenTronClass = onOpenTronClass,
                onCreate = { editorTodo = null; showEditor = true },
            )
            is TodoState.Success -> {
                val success = state as TodoState.Success
                TodoListContent(
                    innerPadding = innerPadding,
                    todos = success.todos,
                    courseOptions = success.courseOptions,
                    syncSummary = syncSummary,
                    syncMessage = autoSyncMessage,
                    onToggle = viewModel::setCompleted,
                    onEdit = { id ->
                        success.todos.firstOrNull { it.id.value == id }?.let { editorTodo = it; showEditor = true }
                    },
                    onDelete = viewModel::deleteTodo,
                    onOpenTronClass = onOpenTronClass,
                    onCreate = { editorTodo = null; showEditor = true },
                )
            }
            is TodoState.Error -> {
                val error = state as TodoState.Error
                TodoListContent(
                    innerPadding = innerPadding,
                    todos = error.todos,
                    courseOptions = error.courseOptions,
                    syncSummary = syncSummary,
                    syncMessage = autoSyncMessage,
                    errorMessage = error.message,
                    onToggle = viewModel::setCompleted,
                    onEdit = { id ->
                        error.todos.firstOrNull { it.id.value == id }?.let { editorTodo = it; showEditor = true }
                    },
                    onDelete = viewModel::deleteTodo,
                    onOpenTronClass = onOpenTronClass,
                    onCreate = { editorTodo = null; showEditor = true },
                )
            }
        }
    }

    if (showEditor) {
        TodoEditorDialog(
            todo = editorTodo,
            courseOptions = when (val current = state) {
                is TodoState.Empty -> current.courseOptions
                is TodoState.Success -> current.courseOptions
                is TodoState.Error -> current.courseOptions
                TodoState.Loading -> emptyList()
            },
            onDismiss = { showEditor = false },
            onSave = { title, description, deadline, course ->
                if (editorTodo == null) {
                    viewModel.addTodo(title, description, deadline, course)
                } else {
                    viewModel.updateTodo(
                        todo = editorTodo!!,
                        title = title,
                        description = description,
                        deadline = deadline,
                        course = course,
                    )
                }
                showEditor = false
            },
        )
    }
}

@Composable
private fun LoadingContent(innerPadding: PaddingValues) {
    Box(
        Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) { AppLoadingState() }
}

@Composable
private fun TodoListContent(
    innerPadding: PaddingValues,
    todos: List<TodoFeatureModel>,
    courseOptions: List<TodoCourseOptionModel>,
    syncSummary: TodoSyncSummary? = null,
    syncMessage: String? = null,
    errorMessage: String? = null,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenTronClass: () -> Unit,
    onCreate: () -> Unit,
) {
    var selectedBucket by remember { mutableStateOf(TodoBucket.ALL) }
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    val visibleTodos = selectedBucket.filter(todos, nowMillis)
    val incomplete = visibleTodos.filter { !it.completed }
    val completed = visibleTodos.filter { it.completed }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .tutorialTarget(TutorialTargetKey.TODO_LIST),
        contentPadding = PaddingValues(
            start = AppSpacing.PagePadding,
            top = AppSpacing.PagePadding,
            end = AppSpacing.PagePadding,
            bottom = AppSpacing.PagePadding,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodoBucket.entries.forEach { bucket ->
                    FilterChip(
                        selected = selectedBucket == bucket,
                        onClick = { selectedBucket = bucket },
                        label = { Text(bucket.label) },
                        modifier = Modifier.testTag("todo_filter_${bucket.name.lowercase()}"),
                    )
                }
            }
        }
        errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        syncMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        syncSummary?.let { summary ->
            item {
                Text(
                    text = if (summary.automatic) {
                        "本次打开 App 自动同步：已同步 ${summary.importedCount} 项"
                    } else {
                        "最近手动同步：已同步 ${summary.importedCount} 项"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (visibleTodos.isEmpty()) {
            item {
                AppEmptyState(
                    title = if (selectedBucket == TodoBucket.ALL) "暂无待办" else "当前时间范围内暂无待办",
                    actionLabel = if (selectedBucket == TodoBucket.ALL) "添加待办" else null,
                    onAction = if (selectedBucket == TodoBucket.ALL) onCreate else null,
                    primaryAction = true,
                )
            }
            if (selectedBucket == TodoBucket.ALL) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onOpenTronClass) { Text("从畅课同步课程和作业") }
                    }
                }
            }
        } else if (selectedBucket == TodoBucket.ALL) {
            groupTodosForDailyView(visibleTodos, nowMillis).forEach { section ->
                todoSection(section.label, section.todos, courseOptions, nowMillis, onToggle, onEdit, onDelete)
            }
        } else {
            todoSection("未完成", incomplete, courseOptions, nowMillis, onToggle, onEdit, onDelete)
            todoSection("已完成", completed, courseOptions, nowMillis, onToggle, onEdit, onDelete)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todoSection(
    title: String,
    todos: List<TodoFeatureModel>,
    courseOptions: List<TodoCourseOptionModel>,
    nowMillis: Long,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (todos.isEmpty()) return
    item {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = AppSpacing.Lg + AppSpacing.Xs,
                top = AppSpacing.Xl,
                bottom = AppSpacing.Sm,
            ),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                todos.forEachIndexed { index, todo ->
                    if (index > 0) {
                        AppListDivider(inset = AppSpacing.Lg + 24.dp + AppSpacing.Md)
                    }
                    TodoItem(todo, courseOptions, nowMillis, onToggle, onEdit, onDelete)
                }
            }
        }
    }
}

@Composable
private fun TodoItem(
    todo: TodoFeatureModel,
    courseOptions: List<TodoCourseOptionModel>,
    nowMillis: Long,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val option = courseOptions.firstOrNull {
        it.reference == todo.courseReference
    }
    val deadlineDisplay = TodoDeadlineDisplay.resolve(
        deadlineMillis = todo.effectiveDeadline(),
        nowMillis = nowMillis,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = AppSpacing.Lg,
                end = AppSpacing.Xs,
                top = AppSpacing.Md,
                bottom = AppSpacing.Md,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        val fillColor = if (todo.completed) MaterialTheme.colorScheme.primary else Color.Transparent
        val ringColor = if (todo.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        // Apple Reminders 风格圆形完成控件：空心环 -> 实心对勾。
        Box(
            modifier = Modifier
                .padding(top = AppSpacing.Xxs)
                .size(48.dp)
                .toggleable(
                    value = todo.completed,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(todo.id.value, it) },
                )
                .semantics {
                    contentDescription = "完成 ${todo.title}"
                    stateDescription = if (todo.completed) "已完成" else "未完成"
                }
                .testTag("todo-checkbox")
                .tutorialTarget(TutorialTargetKey.TODO_CHECKBOX),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(2.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (todo.completed) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = AppSpacing.Md)) {
            Text(
                todo.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (todo.description.isNotBlank()) {
                Text(
                    todo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 优先级：标题 > 截止 > 状态 > 来源（来源降为 caption metadata）。
            val deadlineLine = todo.effectiveDeadline()?.toDisplayDateTime()
            // 3 天以外的倒计时（还有 73xxxD）是噪声，只显示日期。
            // 已完成事项不再显示倒计时与逾期色，避免「已完成却仍在逾期」的矛盾状态。
            val countdown =
                if (todo.completed || deadlineDisplay.urgency == TodoUrgency.NORMAL) null else deadlineDisplay.text
            if (deadlineLine != null || countdown != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    deadlineLine?.let {
                        Text(
                            text = "截止 $it",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    countdown?.let { label ->
                        val tone = when (deadlineDisplay.urgency) {
                            TodoUrgency.OVERDUE, TodoUrgency.CRITICAL -> StatusTone.Error
                            TodoUrgency.URGENT -> StatusTone.Warning
                            TodoUrgency.UPCOMING -> StatusTone.Info
                            TodoUrgency.NORMAL -> StatusTone.Neutral
                        }
                        AppStatusChip(
                            label = label,
                            modifier = Modifier.testTag("todo_deadline_status"),
                            tone = tone,
                        )
                    }
                }
            }
            Text(
                (option?.name ?: "未关联课程") + " · " +
                    if (todo.source == TodoFeatureSource.EXTERNAL) "来自畅课" else "本地",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        var showActions by remember(todo.id.value) { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { showActions = true },
                modifier = Modifier.testTag("todo_actions"),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "待办操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                DropdownMenuItem(
                    text = { Text("编辑待办") },
                    onClick = {
                        showActions = false
                        onEdit(todo.id.value)
                    },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("删除待办") },
                    onClick = {
                        showActions = false
                        onDelete(todo.id.value)
                    },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditorDialog(
    todo: TodoFeatureModel?,
    courseOptions: List<TodoCourseOptionModel>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, TodoCourseOptionModel?) -> Unit,
) {
    var title by remember(todo?.id?.value) { mutableStateOf(todo?.title.orEmpty()) }
    var description by remember(todo?.id?.value) { mutableStateOf(todo?.description.orEmpty()) }
    var deadline by remember(todo?.id?.value) { mutableStateOf(todo?.deadline) }
    var deadlineIsDateOnly by remember(todo?.id?.value) {
        mutableStateOf(
            todo?.source == TodoFeatureSource.LOCAL &&
                todo.deadline?.let(TodoDatePolicy::isDateOnlyValue) == true,
        )
    }
    var selectedCourse by remember(todo?.id?.value, courseOptions) {
        mutableStateOf(courseOptions.firstOrNull { it.reference == todo?.courseReference })
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCourseMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (todo == null) "新增待办" else "编辑待办") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .testTag("todo_editor_scroll_content"),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    isError = title.isBlank(),
                    modifier = Modifier.testTag("todo_editor_title"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    minLines = 2,
                    modifier = Modifier.testTag("todo_editor_description"),
                )
                Box {
                    OutlinedButton(onClick = { showCourseMenu = true }) {
                        Text(selectedCourse?.let { "关联：${it.name}" } ?: "不关联课程")
                    }
                    DropdownMenu(
                        expanded = showCourseMenu,
                        onDismissRequest = { showCourseMenu = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("不关联课程") },
                            onClick = { selectedCourse = null; showCourseMenu = false },
                        )
                        courseOptions.forEach { option ->
                            DropdownMenuItem(
                        text = { Text("${if (option.reference.featureSource == TodoFeatureSource.EXTERNAL) "畅课" else "本地"} · ${option.name}") },
                                onClick = { selectedCourse = option; showCourseMenu = false },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.testTag("todo_editor_deadline"),
                ) {
                    val displayDeadline = deadline?.let {
                        TodoDatePolicy.effectiveDeadline(
                            it,
                            isDateOnly = deadlineIsDateOnly,
                        )
                    }
                    Text(displayDeadline?.toDisplayDateTime() ?: "设置截止日期")
                }
                if (deadline != null) {
                    TextButton(
                        onClick = { deadline = null; deadlineIsDateOnly = false },
                        modifier = Modifier.testTag("todo_editor_clear_deadline"),
                    ) {
                        Text("清除截止日期")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, deadline, selectedCourse) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("todo_editor_save"),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("todo_editor_cancel")) {
                Text("取消")
            }
        },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TodoDatePolicy.pickerDateMillis(
                deadline,
                isDateOnly = deadlineIsDateOnly,
            ),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            val keepDateOnlySemantics = deadline == null || deadlineIsDateOnly
                            deadline = TodoDatePolicy.updateDeadlineDate(
                                existingDeadlineMillis = deadline,
                                selectedUtcDateMillis = selectedDate,
                                existingIsDateOnly = deadlineIsDateOnly,
                            )
                            deadlineIsDateOnly = keepDateOnlySemantics
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("todo_editor_datepicker_confirm"),
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.testTag("todo_editor_datepicker_cancel"),
                ) { Text("取消") }
            },
        ) { DatePicker(state = datePickerState) }
    }
}

private fun Long.toDisplayDateTime(): String {
    val localDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    return if (TodoDatePolicy.isEndOfLocalDay(this)) {
        "${localDateTime.toLocalDate()}（当天结束）"
    } else {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(localDateTime)
    }
}

private fun TodoBucket.filter(todos: List<TodoFeatureModel>, nowMillis: Long): List<TodoFeatureModel> {
    if (this == TodoBucket.ALL) return todos
    val oneDay = nowMillis + 1.days.inWholeMilliseconds
    val threeDays = nowMillis + 3.days.inWholeMilliseconds
    val sevenDays = nowMillis + 7.days.inWholeMilliseconds
    return todos.filter { todo ->
        if (todo.completed) return@filter false
        val deadline = todo.effectiveDeadline() ?: return@filter this == TodoBucket.LONG_TERM
        when (this) {
            TodoBucket.OVERDUE -> TodoDeadlinePolicy.isOverdue(deadline, nowMillis)
            TodoBucket.ONE_DAY -> deadline > nowMillis && deadline <= oneDay
            TodoBucket.THREE_DAYS -> deadline > oneDay && deadline <= threeDays
            TodoBucket.SEVEN_DAYS -> deadline > threeDays && deadline <= sevenDays
            TodoBucket.LONG_TERM -> deadline > sevenDays
            TodoBucket.ALL -> true
        }
    }
}

@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onOpenTronClass: () -> Unit = {},
    openEditorRequest: Boolean = false,
    onOpenEditorRequestConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TutorialHost(tutorialId = "todo", modifier = modifier.fillMaxSize()) {
        TodoScreenContent(
            viewModel = viewModel,
            onOpenTronClass = onOpenTronClass,
            openEditorRequest = openEditorRequest,
            onOpenEditorRequestConsumed = onOpenEditorRequestConsumed,
            modifier = Modifier,
        )
    }
}
