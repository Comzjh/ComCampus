package com.xmu.course.ui.timetable

import com.xmu.course.ui.theme.action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.xmu.course.ui.components.AppIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.xmu.course.ui.components.AppTextButton as TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.testTag
import com.xmu.course.domain.Course
import com.xmu.course.ui.background.TimetableBackground
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.tutorialTarget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * 课表页：周视图 + HorizontalPager 周切换 + 当前周定位 + 课程详情 + 手动添加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableScreenContent(
    modifier: Modifier = Modifier,
    initialCourseId: Long? = null,
    onInitialCourseConsumed: (Long?) -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: TimetableViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // 时间轴文字样式为全局显示偏好，经 ViewModel 透传，设置页修改后即时生效。
    val axisStyle by viewModel.axisStyle.collectAsState()
    val pagerState = rememberPagerState(initialPage = state.viewWeek - 1, pageCount = { state.totalWeeks })
    var showAddDialog by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }
    val verticalScroll = rememberScrollState()
    val viewingWeek by remember {
        derivedStateOf {
            if (state.courses.isEmpty()) state.viewWeek else pagerState.currentPage + 1
        }
    }

    // 先完成 ViewModel 的定位请求，再接收手动翻页；避免初始第 1 页抢先回写。
    // 定位和观察共用一个 effect，新会话或选周会取消旧观察与未完成的滚动。
    LaunchedEffect(
        pagerState, state.timetable?.id, state.totalWeeks, state.hasData,
        state.viewWeek, state.weekResetGeneration,
    ) {
        val timetableId = state.timetable?.id ?: return@LaunchedEffect
        if (!state.hasData) return@LaunchedEffect
        val requestedWeek = state.viewWeek
        val resetGeneration = state.weekResetGeneration
        pagerState.scrollToPage(requestedWeek - 1)
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, page) -> page + 1 }
            .distinctUntilChanged()
            .collect { settledWeek ->
                val latest = viewModel.uiState.value
                // 新请求可能先于下一次重组到达，旧 Pager 事件不能覆盖它。
                if (latest.timetable?.id == timetableId && latest.viewWeek == requestedWeek &&
                    latest.weekResetGeneration == resetGeneration && settledWeek != requestedWeek
                ) {
                    viewModel.selectWeek(settledWeek)
                }
            }
    }

    // Widget 点击后打开对应课程详情；详情展示一次后清除请求。
    LaunchedEffect(initialCourseId, state.courses) {
        val requested = initialCourseId ?: return@LaunchedEffect
        state.courses.firstOrNull { it.id == requested }?.let {
            detailCourse = it
            onInitialCourseConsumed(requested)
        }
    }
    fun goToWeek(week: Int) {
        viewModel.selectWeek(week)
    }

    Box(modifier.fillMaxSize()) {
        // 背景在最底层；不参与课程位置、高度、宽度或冲突分栏计算。
        if (state.hasData) TimetableBackground(state.config)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (state.config.headerCompactMode) {
                    TimetableCompactHeader(
                        title = if (state.hasData) {
                            val suffix = if (viewingWeek == state.actualWeek) " · 本周" else ""
                            "第 $viewingWeek 周$suffix ▼"
                        } else {
                            state.timetable?.name ?: "ComCampus"
                        },
                        subtitle = if (state.hasData) {
                            state.timetable?.name ?: "ComCampus"
                        } else {
                            "尚无数据，请先导入"
                        },
                        onTitleClick = { if (state.hasData) showWeekPicker = true },
                        onAddClick = {
                            if (state.hasData) {
                                addPrefill = null
                                showAddDialog = true
                            }
                        },
                        onImportClick = onOpenImport,
                        onSettingsClick = onOpenSettings,
                        onBackToCurrent = if (state.hasData && viewingWeek != state.actualWeek) {
                            { goToWeek(state.actualWeek) }
                        } else {
                            null
                        },
                        weekPickerEnabled = state.hasData,
                        addEnabled = state.hasData,
                    )
                } else {
                    TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TIMETABLE_HEADER_OVERLAY_ALPHA),
                    ),
                    title = {
                        Column {
                            Text(
                                text = if (state.hasData) {
                                    val suffix = if (viewingWeek == state.actualWeek) " · 本周" else ""
                                    "第 $viewingWeek 周$suffix ▼"
                                } else {
                                    state.timetable?.name ?: "ComCampus"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable(enabled = state.hasData) { showWeekPicker = true }
                                    .tutorialTarget(TutorialTargetKey.TIMETABLE_WEEK_SWITCH),
                            )
                            Text(
                                text = if (state.hasData) state.timetable?.name ?: "ComCampus" else "尚无数据，请先导入",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        if (state.hasData && viewingWeek != state.actualWeek) {
                            TextButton(
                                onClick = { goToWeek(state.actualWeek) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.action,
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            ) { Text("回到本周") }
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.tutorialTarget(TutorialTargetKey.TIMETABLE_SETTINGS),
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "课表设置",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onOpenImport) {
                            Icon(
                                Icons.Filled.CloudDownload,
                                contentDescription = "导入课表",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        TimetableAddCourseAction(
                            onClick = {
                                addPrefill = null
                                showAddDialog = true
                            },
                            enabled = state.hasData,
                        )
                        TutorialToolbarAction()
                    },
                    )
                }
            },
        ) { innerPadding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            !state.hasData || state.timetable == null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                AppEmptyState(
                    title = "还没有课表",
                    description = "导入课表后才能开始计算教学周",
                    actionLabel = "导入课表",
                    onAction = onOpenImport,
                    modifier = Modifier.testTag("timetable_empty"),
                )
            }

            state.courses.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                AppEmptyState(
                    title = "暂无课程",
                    description = "请先导入厦大课表，或点右上角 + 手动添加",
                    actionLabel = "添加课程",
                    onAction = {
                        addPrefill = null
                        showAddDialog = true
                    },
                    modifier = Modifier.testTag("timetable_no_courses"),
                )
            }

            else -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("timetable_week_pager"),
            ) { page ->
                Box(Modifier.fillMaxSize().testTag("timetable_week_page_${page + 1}")) {
                    TimetableWeekGrid(
                        week = page + 1,
                        courses = state.courses,
                        skippedCourseIds = state.skippedCourseIds,
                        showGrid = state.showGrid,
                        config = state.config,
                        axisStyle = axisStyle,
                        startDate = state.timetable?.startDate,
                        verticalScroll = verticalScroll,
                        onCourseClick = { detailCourse = it },
                        onCellClick = { day, section ->
                            addPrefill = day to section
                            showAddDialog = true
                        },
                    )
                }
            }
        }
    }

    // ---- 周选择器 ----
    if (showWeekPicker && state.hasData) {
        WeekPickerSheet(
            totalWeeks = state.totalWeeks,
            viewWeek = state.viewWeek,
            actualWeek = state.actualWeek,
            onSelect = {
                showWeekPicker = false
                goToWeek(it)
            },
            onBackToCurrent = {
                showWeekPicker = false
                goToWeek(state.actualWeek)
            },
            onDismiss = { showWeekPicker = false },
        )
    }

    // 对话框挂在 Scaffold 之外，保证任何分支下都能弹出（Bug 4 修复）。
    if (showAddDialog) {
        AddCourseDialog(
            initialDayOfWeek = addPrefill?.first,
            initialStartSection = addPrefill?.second,
            onDismiss = {
                showAddDialog = false
                addPrefill = null
            },
            onSave = {
                viewModel.addCourse(it)
                showAddDialog = false
                addPrefill = null
            },
        )
    }

    detailCourse?.let { course ->
        CourseDetailSheet(
            course = course,
            tronCourseMatch = state.tronCourseLinks[course.id],
            isSkipped = course.id in state.skippedCourseIds,
            onDismiss = { detailCourse = null },
            onToggleSkipped = { skipped ->
                viewModel.toggleCourseSkipped(course.id, skipped)
                detailCourse = null
            },
            onColorChange = { hex ->
                viewModel.updateCourseColor(course.id, hex)
                detailCourse = null
            },
            onNoteChange = { note ->
                viewModel.updateCourseNote(course.id, note)
            },
        )
    }
    }
}

@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    initialCourseId: Long? = null,
    onInitialCourseConsumed: (Long?) -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: TimetableViewModel = viewModel(),
) {
    TutorialHost(tutorialId = "timetable", modifier = modifier.fillMaxSize()) {
        TimetableScreenContent(
            modifier = Modifier,
            initialCourseId = initialCourseId,
            onInitialCourseConsumed = onInitialCourseConsumed,
            onOpenImport = onOpenImport,
            onOpenSettings = onOpenSettings,
            viewModel = viewModel,
        )
    }
}
