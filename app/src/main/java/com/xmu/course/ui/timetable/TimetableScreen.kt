package com.xmu.course.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.background.TimetableBackground

private val CELL_HEIGHT = 56.dp

/**
 * 课表页：周视图 + HorizontalPager 周切换 + 当前周定位 + 课程详情 + 手动添加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    initialCourseId: Long? = null,
    onInitialCourseConsumed: (Long?) -> Unit = {},
    viewModel: TimetableViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = state.viewWeek - 1, pageCount = { state.totalWeeks })
    var showAddDialog by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }

    // 查看周变化（首次进入默认定位实际周；周选择器选择/回到本周也会触发）。
    LaunchedEffect(state.viewWeek) {
        if (state.hasData) pagerState.scrollToPage(state.viewWeek - 1)
    }
    LaunchedEffect(state.courses) {
        Log.d("XmuImport", "Screen: 渲染 courses=${state.courses.size}")
    }

    // Widget 点击后打开对应课程详情；详情展示一次后清除请求。
    LaunchedEffect(initialCourseId, state.courses) {
        val requested = initialCourseId ?: return@LaunchedEffect
        state.courses.firstOrNull { it.id == requested }?.let {
            detailCourse = it
            onInitialCourseConsumed(requested)
        }
    }
    // Pager 滑动同步回状态。
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectWeek(pagerState.currentPage + 1)
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
                            val suffix = if (pagerState.currentPage + 1 == state.actualWeek) " · 本周" else ""
                            "第 ${pagerState.currentPage + 1} 周$suffix ▼"
                        } else {
                            state.timetable?.name ?: "XMU Course"
                        },
                        subtitle = if (state.hasData) {
                            state.timetable?.name ?: "XMU Course"
                        } else {
                            "尚无数据，请先导入"
                        },
                        onTitleClick = { if (state.hasData) showWeekPicker = true },
                        onAddClick = { if (state.hasData) showAddDialog = true },
                    )
                } else {
                    TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = {
                        Column {
                            Text(
                                text = if (state.hasData) {
                                    val suffix = if (pagerState.currentPage + 1 == state.actualWeek) " · 本周" else ""
                                    "第 ${pagerState.currentPage + 1} 周$suffix ▼"
                                } else {
                                    state.timetable?.name ?: "XMU Course"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.clickable(enabled = state.hasData) { showWeekPicker = true },
                            )
                            Text(
                                text = if (state.hasData) state.timetable?.name ?: "XMU Course" else "尚无数据，请先导入",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }, enabled = state.hasData) {
                            Icon(Icons.Filled.Add, contentDescription = "添加课程")
                        }
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
            ) { Text("还没有课表\n去“导入”页保存课表，或在“设置 → 课表管理”新建", textAlign = TextAlign.Center) }

            state.courses.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无课程\n请先导入厦大课表，或点右上角 + 手动添加",
                    textAlign = TextAlign.Center,
                )
            }

            else -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) { page ->
                TimetableWeekGrid(
                    week = page + 1,
                    courses = state.courses,
                    skippedCourseIds = state.skippedCourseIds,
                    showGrid = state.showGrid,
                    config = state.config,
                    onCourseClick = { detailCourse = it },
                )
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
                viewModel.selectWeek(it)
                showWeekPicker = false
            },
            onBackToCurrent = {
                viewModel.backToCurrentWeek()
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
    }

    // 对话框挂在 Scaffold 之外，保证任何分支下都能弹出（Bug 4 修复）。
    if (showAddDialog) {
        AddCourseDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.addCourse(it)
                showAddDialog = false
            },
        )
    }

    detailCourse?.let { course ->
        CourseDetailSheet(
            course = course,
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
private fun TimetableCompactHeader(
    title: String,
    subtitle: String,
    onTitleClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding)
            .padding(start = 12.dp, end = 2.dp, top = 0.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onTitleClick),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加课程", modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 单周网格：左侧节次（含时间）+ 周日~周六 7 列，课程卡片按布局引擎放置。
 */
@Composable
private fun TimetableWeekGrid(
    week: Int,
    courses: List<Course>,
    skippedCourseIds: Set<Long>,
    showGrid: Boolean,
    config: TimetableConfig,
    onCourseClick: (Course) -> Unit,
) {
    val cellHeight = config.courseHeight.dp
    // TimeColumn 和 Grid 使用同一纵向布局配置，禁止各自独立推算高度。
    val layoutConfig = TimetableLayoutConfig(cellHeight.value)
    // showNonCurrentWeek=false 时隐藏非本周课程
    val layout = TimetableLayoutEngine.layoutForWeek(
        courses, week, onlyCurrentWeek = !config.showNonCurrentWeek,
    )
    val scrollState = rememberScrollState()
    val sectionCount = TimeTableConfig.sectionCount
    val axisTypography = timeAxisTypography(cellHeight.value)
    // 列顺序：周日（可选）+ 周一~周五 + 周六（可选），与金智一致
    val dayColumns: List<Int> = buildList {
        if (config.showSunday) add(7)
        addAll(listOf(1, 2, 3, 4, 5))
        if (config.showSaturday) add(6)
    }
    val dayNames = mapOf(7 to "周日", 1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Text("", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall)
            dayColumns.forEach { day ->
                Text(
                    text = dayNames.getValue(day),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider()

        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // 左侧节次标号 + 起止时间（两行，避免截断）。
            Column(Modifier.width(60.dp)) {
                // 分隔线画在固定高度的 Box 内部，不能使用额外 HorizontalDivider。
                // 否则每节累计 +1dp，时间轴会逐渐偏离右侧网格线。
                val axisLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                repeat(sectionCount) { index ->
                    Box(
                        Modifier
                            .height(cellHeight)
                            .fillMaxWidth()
                            .drawBehind {
                                if (showGrid) {
                                    drawLine(
                                        color = axisLineColor,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(axisTypography.spacingDp.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = axisTypography.numberSizeSp.sp,
                                lineHeight = axisTypography.numberLineHeightSp.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (config.showFullTimeAxis) {
                                val parts = TimeTableConfig.timeOf(index + 1).split("-")
                                Text(
                                    text = parts.getOrNull(0) ?: "",
                                    fontSize = axisTypography.timeSizeSp.sp,
                                    lineHeight = axisTypography.timeLineHeightSp.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = parts.getOrNull(1) ?: "",
                                    fontSize = axisTypography.timeSizeSp.sp,
                                    lineHeight = axisTypography.timeLineHeightSp.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            // 天列。
            dayColumns.forEachIndexed { colIndex, day ->
                DayColumn(
                    dayOfWeek = day,
                    items = layout[day].orEmpty(),
                    skippedCourseIds = skippedCourseIds,
                    showGrid = showGrid,
                    layoutConfig = layoutConfig,
                    config = config,
                    onCourseClick = onCourseClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 单天列：背景节次分隔线 + 课程卡片（冲突分栏）。
 */
@Composable
private fun DayColumn(
    dayOfWeek: Int,
    items: List<TimetableLayoutItem>,
    skippedCourseIds: Set<Long>,
    showGrid: Boolean,
    layoutConfig: TimetableLayoutConfig,
    config: TimetableConfig,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellHeight = config.courseHeight.dp
    BoxWithConstraints(
        modifier = modifier.height(cellHeight * TimeTableConfig.sectionCount),
    ) {
        // 渲染顺序：先画 ghost（非本周淡化，独立布局），再画本周课程覆盖其上。
        val activeItems = items.filter { it.isCurrentWeek }
        android.util.Log.d(
            "XmuTimetable",
            "DayColumn: active=${activeItems.size}, ghost=${items.size - activeItems.size}, " +
                activeItems.joinToString { "${it.course.name}(d${it.course.dayOfWeek},s${it.course.startSection},dur${it.course.duration},lane${it.laneIndex}/${it.laneCount})" },
        )
        val ghostItems = items.filter { !it.isCurrentWeek }

        // 网格只作为 Canvas 视觉层：不新增 padding/size，也不改变课程 Box 的约束。
        if (showGrid) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 1.dp.toPx()
                val rowHeight = cellHeight.toPx()
                // 横向节次线（1..sectionCount），与课程 y=(start-1)*cellHeight 对齐。
                for (index in 1..layoutConfig.sectionCount) {
                    val y = layoutConfig.gridLineYDp(index).dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                }
                // 天列右边界；列与列之间连续形成竖向分隔线。
                drawLine(
                    color = lineColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                )
            }
        }

        // 宽度由所在冲突组的 laneCount 决定（组内均分）；高度只由节次决定。
        (ghostItems + activeItems).forEach { item ->
            val course = item.course
            val slotWidth = maxWidth / maxOf(item.laneCount, 1)
            val layoutHeight = cellHeight * course.duration
            val renderHeight = layoutHeight
            val offsetX = slotWidth * item.laneIndex
            val offsetY = cellHeight * (course.startSection - 1)
            android.util.Log.d(
                "XmuTimetable",
                "CourseCard: showGrid=$showGrid, name=${course.name}, duration=${course.duration}, " +
                    "cellHeight=${cellHeight.value}dp, layoutHeight=${layoutHeight.value}dp, " +
                    "renderHeight=${renderHeight.value}dp, lane=${item.laneIndex}/${item.laneCount}, " +
                    "offsetX=${offsetX.value}dp, offsetY=${offsetY.value}dp, width=${slotWidth.value}dp",
            )
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .width(slotWidth)
                    .height(renderHeight)
                    .padding(1.dp),
            ) {
                CourseCard(
                    course = course,
                    isCurrentWeek = item.isCurrentWeek,
                    isSkipped = course.id in skippedCourseIds,
                    showTeacher = config.showTeacher,
                    showLocation = config.showLocation,
                    showNote = config.showNote,
                    textSize = config.textSize,
                    cornerRadius = config.cornerRadius,
                    cardAlpha = config.courseAlpha,
                    textHorizontalAlignment = config.textHorizontalAlignment,
                    textVerticalAlignment = config.textVerticalAlignment,
                    onClick = { onCourseClick(course) },
                )
            }
        }
    }
}

/**
 * 周选择器 BottomSheet：动态 1..totalWeeks 四列网格。
 * 实际周描边提示，查看周高亮填充；"回到本周"一键复位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerSheet(
    totalWeeks: Int,
    viewWeek: Int,
    actualWeek: Int,
    onSelect: (Int) -> Unit,
    onBackToCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("选择周", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    onBackToCurrent()
                    onDismiss()
                }) { Text("回到本周") }
            }
            val rows = (totalWeeks + 3) / 4
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height((rows * 56).dp),
            ) {
                items((1..totalWeeks).toList()) { week ->
                    val selected = week == viewWeek
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(shape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .then(
                                if (week == actualWeek && !selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                onSelect(week)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$week",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else null,
                        )
                    }
                }
            }
        }
    }
}

