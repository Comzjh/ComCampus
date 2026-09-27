package com.xmu.course.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.BuildConfig
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.data.update.UpdateCheckResult
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppLargeTitleHeader
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.tutorial.TutorialAutoScroll
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget
import com.xmu.course.ui.theme.ThemePreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 设置页：Apple Settings 风格分组卡（学期 / 同步 / 外观 / 课表显示 / 数据管理 / 更新 / 关于）。
 *
 * 只编排既有回调与 ViewModel，不新增业务逻辑。
 */
internal fun startDatePickerMillis(startDate: String?): Long? = runCatching {
    startDate?.let {
        LocalDate.parse(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    onOpenTimetableManager: () -> Unit = {},
    onOpenCourseManager: () -> Unit = {},
    onOpenTimetableSettings: () -> Unit = {},
    onOpenBackgroundPicker: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    automaticUpdateCheckEnabled: Boolean = true,
    updateCheckInProgress: Boolean = false,
    updateCheckResult: UpdateCheckResult = UpdateCheckResult.NotChecked,
    onAutomaticUpdateCheckChanged: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onAutoRefreshTodoChanged: (Boolean) -> Unit = {},
    onReplayGuide: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    ThemePreferences.ensureLoaded(context)
    val followSystemColor by ThemePreferences.followSystemColor.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        TutorialAutoScroll(scrollState, "settings", innerPadding.calculateTopPadding())
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(AppSpacing.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
        ) {
            AppLargeTitleHeader(title = "设置", trailingContent = { TutorialToolbarAction() })
            // ---- 个人中心 ----
            AppGroupedSection(title = "账户", modifier = Modifier.tutorialTarget(TutorialTargetKey.SETTINGS_ACCOUNT)) {
                AppNavigationRow(
                    title = "个人中心",
                    description = "登录、数据与支持",
                    onClick = onOpenProfile,
                )
            }

            // ---- 外观 ----
            AppGroupedSection(title = "外观", modifier = Modifier.tutorialTarget(TutorialTargetKey.SETTINGS_APPEARANCE)) {
                SettingSwitch(
                    title = "跟随系统色彩",
                    subtitle = "使用壁纸配色（Android 12+）；关闭则使用品牌蓝",
                    checked = followSystemColor,
                    onChange = { ThemePreferences.setFollowSystemColor(context, it) },
                )
            }

            // ---- 启动 ----
            AppGroupedSection(title = "启动") {
                StartupDestinationChoice(
                    current = state.startupDestination,
                    onSelect = { viewModel.setStartupDestination(it) },
                )
            }


            // ---- 当前本机课表学期 ----
            AppGroupedSection(title = "当前课表学期") {
                val semester = state.semester
                if (semester == null) {
                    Text(
                        "本机还没有课表，请先导入课表",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(semester.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "开学日期：" + (semester.startDate ?: "未设置"),
                        color = if (semester.startDate == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.padding(top = AppSpacing.Sm),
                    ) {
                        Text(if (semester.startDate == null) "设置开学日期" else "修改开学日期")
                    }
                }
                Text(
                    "学业课程与本机课表独立，需要另行导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.Sm),
                )
            }

            // ---- TronClass 同步 ----
            AppGroupedSection(title = "畅课同步") {
                SettingSwitch(
                    title = "自动导入畅课作业",
                    subtitle = "同步畅课时，将作业加入待办",
                    checked = state.autoImportAssignments,
                    onChange = viewModel::setAutoImportAssignments,
                )
                SectionDivider()
                SettingSwitch(
                    title = "自动刷新畅课待办",
                    subtitle = "仅在应用前台刷新，每小时最多一次",
                    checked = state.autoRefreshTodo,
                    onChange = onAutoRefreshTodoChanged,
                )
            }

            // ---- 课表显示 ----
            AppGroupedSection(title = "课表显示", modifier = Modifier.tutorialTarget(TutorialTargetKey.SETTINGS_TIMETABLE)) {
                AppNavigationRow(
                    title = "课表背景",
                    description = "为课表设置壁纸或纯色背景",
                    icon = Icons.Filled.Image,
                    onClick = onOpenBackgroundPicker,
                )
                SectionDivider()
                AppNavigationRow(
                    title = "当前课表显示设置",
                    description = "网格线、课程信息显隐、周末与非本周课程",
                    icon = Icons.Filled.Tune,
                    onClick = onOpenTimetableSettings,
                )
                Text(
                    "显示设置按课表分别保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.Sm),
                )
            }

            // ---- 数据管理 ----
            AppGroupedSection(title = "数据管理") {
                AppNavigationRow(
                    title = "课表管理",
                    description = "创建、切换与删除课表",
                    onClick = onOpenTimetableManager,
                )
                SectionDivider()
                AppNavigationRow(
                    title = "课程管理",
                    description = "编辑课程名称、教师与地点",
                    onClick = onOpenCourseManager,
                )
            }

            // ---- 危险操作：独立分区，与常规导航行在视觉与点击上双重隔离 ----
            AppSectionCard(title = "危险操作") {
                Text(
                    "只删除这台手机上已导入或手动添加的本学期课程，不会修改学校系统中的数据；删除后无法从应用内恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { showDeleteConfirm = true },
                    enabled = state.semester != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier
                        .testTag("settings_delete_current_timetable")
                        .padding(top = AppSpacing.Sm)
                        .align(Alignment.Start),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "删除当前学期课表",
                        modifier = Modifier.padding(start = AppSpacing.Xs),
                    )
                }
            }

            // ---- 应用更新 ----
            AppGroupedSection(
                title = "应用更新",
                modifier = Modifier.tutorialTarget(TutorialTargetKey.SETTINGS_UPDATE),
            ) {
                SettingSwitch(
                    title = "自动检查更新",
                    subtitle = "启动应用时检查 GitHub Releases，每 24 小时最多一次；有更新时进入设置再提示",
                    checked = automaticUpdateCheckEnabled,
                    onChange = onAutomaticUpdateCheckChanged,
                )
                Button(
                    onClick = onCheckForUpdates,
                    enabled = !updateCheckInProgress,
                    modifier = Modifier
                        .padding(top = AppSpacing.Sm)
                        .align(androidx.compose.ui.Alignment.Start),
                ) {
                    Text(if (updateCheckInProgress) "检查中…" else "检查更新")
                }
                if (updateCheckResult is UpdateCheckResult.UpToDate) {
                    Text(
                        "当前已是最新版本",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (updateCheckResult is UpdateCheckResult.Available) {
                    Text(
                        text = "发现新版本 ${updateCheckResult.latestVersion}；点击「检查更新」可重新查看安装选项。",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("settings_update_available"),
                    )
                }
            }

            // ---- 帮助与教程：E6，指南不是一次性资源，任何时候都能重看 ----
            AppGroupedSection(title = "帮助与教程") {
                AppNavigationRow(
                    title = "新手指南",
                    description = "重看首次启动的引导页",
                    onClick = onReplayGuide,
                    modifier = Modifier.testTag("settings_replay_guide"),
                )
                SectionDivider()
                AppNavigationRow(
                    title = "功能指南",
                    description = "按章节查看课表、待办、学业与数据的完整说明",
                    onClick = onOpenGuide,
                    modifier = Modifier.testTag("settings_open_feature_guide"),
                )
            }
            // ---- 关于 ----
            AppGroupedSection(title = "关于", modifier = Modifier.tutorialTarget(TutorialTargetKey.SETTINGS_ABOUT)) {
                Text("ComCampus", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("厦大学生本地课表 · Made for XMU students")
                Text(
                    "ComCampus 为非学校官方应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_about_unofficial"),
                )
                Text(
                    "开源协议：GNU GPL-3.0-only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    "本地优先 · 无服务器 · 不收集任何数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- 开学日期选择 ----
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDatePickerMillis(state.semester?.startDate),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.saveStartDate(date.toString())
                    }
                    showDatePicker = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ---- 删除二次确认 ----
    if (showDeleteConfirm) {
        var deleteSemesterToo by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("将删除本机保存的本学期全部课程，不会修改学校系统中的数据，也无法从应用内恢复。")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = AppSpacing.Sm),
                    ) {
                        Checkbox(checked = deleteSemesterToo, onCheckedChange = { deleteSemesterToo = it })
                        Text("同时删除学期记录")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTimetable(deleteSemesterToo)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StartupDestinationChoice(
    current: StartupDestination,
    onSelect: (StartupDestination) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Xs),
    ) {
        Text("默认打开页面", style = MaterialTheme.typography.bodyLarge)
        Text(
            "选择打开应用时首先显示的页面",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(top = AppSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            StartupChoiceButton(
                label = "首页",
                selected = current == StartupDestination.HOME,
                tag = "settings_startup_home",
                onClick = { onSelect(StartupDestination.HOME) },
            )
            StartupChoiceButton(
                label = "课表",
                selected = current == StartupDestination.TIMETABLE,
                tag = "settings_startup_timetable",
                onClick = { onSelect(StartupDestination.TIMETABLE) },
            )
        }
    }
}

@Composable
private fun StartupChoiceButton(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val choiceModifier = Modifier.testTag(tag).semantics {
        this.selected = selected
        role = Role.RadioButton
    }
    if (selected) {
        Button(onClick = onClick, modifier = choiceModifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = choiceModifier) { Text(label) }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
fun SettingsScreen(
    onOpenTimetableManager: () -> Unit = {},
    onOpenCourseManager: () -> Unit = {},
    onOpenTimetableSettings: () -> Unit = {},
    onOpenBackgroundPicker: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    automaticUpdateCheckEnabled: Boolean = true,
    updateCheckInProgress: Boolean = false,
    updateCheckResult: UpdateCheckResult = UpdateCheckResult.NotChecked,
    onAutomaticUpdateCheckChanged: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onAutoRefreshTodoChanged: (Boolean) -> Unit = {},
    onReplayGuide: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    TutorialHost(tutorialId = "settings", modifier = modifier.fillMaxSize()) {
        SettingsScreenContent(
            onOpenTimetableManager = onOpenTimetableManager,
            onOpenCourseManager = onOpenCourseManager,
            onOpenTimetableSettings = onOpenTimetableSettings,
            onOpenBackgroundPicker = onOpenBackgroundPicker,
            onOpenProfile = onOpenProfile,
            automaticUpdateCheckEnabled = automaticUpdateCheckEnabled,
            updateCheckInProgress = updateCheckInProgress,
            updateCheckResult = updateCheckResult,
            onAutomaticUpdateCheckChanged = onAutomaticUpdateCheckChanged,
            onCheckForUpdates = onCheckForUpdates,
            onAutoRefreshTodoChanged = onAutoRefreshTodoChanged,
            onReplayGuide = onReplayGuide,
            onOpenGuide = onOpenGuide,
            modifier = Modifier,
            viewModel = viewModel,
        )
    }
}
