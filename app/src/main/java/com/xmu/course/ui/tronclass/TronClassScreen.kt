package com.xmu.course.ui.tronclass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xmu.course.data.tronclass.model.TronCourseUiModel
import com.xmu.course.data.tronclass.model.sortTronSemesterLabels
import com.xmu.course.data.tronclass.model.toSafeMessage
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppLoadingState
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.AppSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 厦大畅课正式用户页。
 *
 * Phase 8.13：统一到 ComCampus 设计语言（状态徽标 / 统一空态与加载态 / 分组卡课程），
 * 仅展示层变化；登录、同步、退出与学期筛选语义保持原样。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TronClassScreen(
    onLaunchAuth: () -> Unit,
    onClearWebData: () -> Unit,
    viewModel: TronClassViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSyncCompleted: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // BUG-02：同步成功后交给导航层一次性回待办；失败/会话过期不会触发。
    LaunchedEffect(uiState.screenState) {
        if (uiState.screenState == TronClassScreenState.Success) onSyncCompleted()
    }

    LaunchedEffect(uiState.clearWebDataRequested) {
        if (uiState.clearWebDataRequested) {
            onClearWebData()
            viewModel.consumeClearWebDataRequest()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("厦大畅课") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState.screenState) {
            TronClassScreenState.CheckingSession -> CheckingSessionContent(innerPadding)
            TronClassScreenState.Unauthenticated -> UnauthenticatedContent(innerPadding, onLaunchAuth)
            else -> AuthenticatedContent(
                state = uiState,
                innerPadding = innerPadding,
                onSync = viewModel::syncCourses,
                onLogout = viewModel::requestLogout,
            )
        }
    }

    if (uiState.showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelLogout,
            title = { Text("退出畅课登录") },
            text = { Text("退出后会清除畅课登录状态和缓存课程。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogout) { Text("确认退出") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelLogout) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CheckingSessionContent(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(AppSpacing.PagePadding),
        verticalArrangement = Arrangement.Center,
    ) {
        AppLoadingState(label = "正在检查畅课登录状态…")
    }
}

@Composable
private fun UnauthenticatedContent(innerPadding: PaddingValues, onLaunchAuth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(AppSpacing.PagePadding),
        verticalArrangement = Arrangement.Center,
    ) {
        AppEmptyState(
            title = "厦大畅课",
            description = "登录后可同步课程；畅课作业与待办会进入待办清单。",
            icon = Icons.Filled.School,
            actionLabel = "登录畅课",
            onAction = onLaunchAuth,
        )
    }
}

/** 运行状态 → 用户文案映射，只发生在 UI 层。 */
private fun tronClassStatusPresentation(
    screenState: TronClassScreenState,
): Pair<String, StatusTone> = when (screenState) {
    TronClassScreenState.Syncing -> "正在同步" to StatusTone.Info
    TronClassScreenState.SessionExpired -> "登录已过期" to StatusTone.Warning
    TronClassScreenState.Error -> "暂不可用" to StatusTone.Error
    else -> "已连接" to StatusTone.Success
}

@Composable
private fun AuthenticatedContent(
    state: TronClassUiState,
    innerPadding: PaddingValues,
    onSync: () -> Unit,
    onLogout: () -> Unit,
) {
    val semesterOptions = remember(state.courses) {
        state.courses
            .map { it.semester.ifBlank { "未标注学期" } }
            .distinct()
            .let(::sortTronSemesterLabels)
    }
    var selectedSemester by rememberSaveable { mutableStateOf<String?>(null) }
    var semesterSelectionInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(semesterOptions) {
        if (!semesterSelectionInitialized && semesterOptions.isNotEmpty()) {
            selectedSemester = semesterOptions.firstOrNull()
            semesterSelectionInitialized = true
        } else if (selectedSemester != null && selectedSemester !in semesterOptions) {
            selectedSemester = semesterOptions.firstOrNull()
        }
    }
    val visibleCourses = if (selectedSemester == null) {
        state.courses
    } else {
        state.courses.filter { it.semester.ifBlank { "未标注学期" } == selectedSemester }
    }
    var semesterMenuExpanded by remember { mutableStateOf(false) }
    val (statusLabel, statusTone) = tronClassStatusPresentation(state.screenState)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(AppSpacing.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "已登录厦大畅课",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                AppStatusChip(label = statusLabel, tone = statusTone)
            }
            Text(
                state.lastSyncTime?.let { "最后同步：${formatTime(it)}" } ?: "尚未同步课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.Xs),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Button(
                    onClick = onSync,
                    enabled = state.screenState != TronClassScreenState.Syncing,
                ) {
                    if (state.screenState == TronClassScreenState.Syncing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = AppSpacing.Sm),
                        )
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                    }
                    Text("同步课程", modifier = Modifier.padding(start = AppSpacing.Sm))
                }
                OutlinedButton(onClick = onLogout) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Text("退出登录", modifier = Modifier.padding(start = AppSpacing.Sm))
                }
            }
        }
        if (state.screenState == TronClassScreenState.Syncing) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "正在同步课程…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.screenState == TronClassScreenState.SessionExpired) {
            item {
                Text(
                    "登录状态已失效，请重新登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.screenState == TronClassScreenState.Success && state.lastSyncCount != null) {
            item {
                Text(
                    "同步完成，共 ${state.lastSyncCount} 门课程",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.lastAssignmentSyncCount?.let { count ->
                    Text(
                        "同时导入 $count 项畅课待办",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (semesterOptions.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "学期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.padding(start = AppSpacing.Sm)) {
                        OutlinedButton(onClick = { semesterMenuExpanded = true }) {
                            Text(selectedSemester ?: "全部学期")
                        }
                        DropdownMenu(
                            expanded = semesterMenuExpanded,
                            onDismissRequest = { semesterMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部学期") },
                                onClick = {
                                    selectedSemester = null
                                    semesterSelectionInitialized = true
                                    semesterMenuExpanded = false
                                },
                            )
                            semesterOptions.forEach { semester ->
                                DropdownMenuItem(
                                    text = { Text(semester) },
                                    onClick = {
                                        selectedSemester = semester
                                        semesterSelectionInitialized = true
                                        semesterMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "已缓存 ${semesterOptions.size} 个学期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = AppSpacing.Sm),
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    error.toSafeMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.courses.isEmpty()) {
            item {
                AppEmptyState(
                    title = "暂无畅课课程",
                    description = "点击「同步课程」拉取最新课程。",
                    icon = Icons.Filled.School,
                )
            }
        } else {
            items(visibleCourses, key = { it.id }) { course ->
                TronCourseCard(course)
            }
        }
    }
}

@Composable
private fun TronCourseCard(course: TronCourseUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Card,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            Text(course.name, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = AppSpacing.Xs),
            )
            Text(
                "教师：${course.instructor.ifBlank { "未提供" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "学期：${course.semester.ifBlank { "未提供" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(timestamp: Long): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
}.getOrDefault("未知")

@Preview(name = "畅课未登录", showBackground = true)
@Composable
private fun TronClassUnauthenticatedPreview() {
    MaterialTheme {
        UnauthenticatedContent(PaddingValues(), onLaunchAuth = {})
    }
}

@Preview(name = "畅课已登录", showBackground = true)
@Composable
private fun TronClassAuthenticatedPreview() {
    MaterialTheme {
        AuthenticatedContent(
            state = TronClassUiState(
                screenState = TronClassScreenState.Authenticated,
                courses = listOf(
                    TronCourseUiModel(
                        id = 1L,
                        name = "示例课程",
                        semester = "示例学期",
                        instructor = "示例教师",
                    ),
                ),
            ),
            innerPadding = PaddingValues(),
            onSync = {},
            onLogout = {},
        )
    }
}
