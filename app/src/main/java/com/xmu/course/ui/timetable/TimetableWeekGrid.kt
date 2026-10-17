package com.xmu.course.ui.timetable


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.xmu.course.ui.theme.action
import com.xmu.course.data.TimetableAxisStyle
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.tutorialTarget
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import java.time.LocalDate

/**
 * 单周网格：左侧节次（含时间）+ 周日~周六 7 列，课程卡片按布局引擎放置。
 */
@Composable
internal fun TimetableWeekGrid(
    week: Int,
    courses: List<Course>,
    skippedCourseIds: Set<Long>,
    showGrid: Boolean,
    config: TimetableConfig,
    axisStyle: TimetableAxisStyle = TimetableAxisStyle(),
    startDate: String?,
    verticalScroll: androidx.compose.foundation.ScrollState,
    onCourseClick: (Course) -> Unit,
    onCellClick: (dayOfWeek: Int, startSection: Int) -> Unit,
) {
    // showNonCurrentWeek=false 时隐藏非本周课程
    val layout = TimetableLayoutEngine.layoutForWeek(
        courses, week, onlyCurrentWeek = !config.showNonCurrentWeek,
    )
    val sectionCount = TimeTableConfig.sectionCount
    // 列顺序：周日（可选）+ 周一~周五 + 周六（可选），与金智一致
    val dayColumns: List<Int> = buildList {
        if (config.showSunday) add(7)
        addAll(listOf(1, 2, 3, 4, 5))
        if (config.showSaturday) add(6)
    }
    val dayNames = mapOf(7 to "周日", 1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六")
    val dates = remember(startDate, week, dayColumns) {
        dayColumns.associateWith { day ->
            TimetableCalendar.dateFor(startDate, week, day)
        }
    }
    val today = LocalDate.now()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val metrics = calculateTimetableLayoutMetrics(maxWidth, config.courseHeight.dp)
        val axisTypography = timeAxisTypography(
            metrics.cellHeight.value, axisStyle.periodFontSp, axisStyle.timeFontSp,
        )
        // Scale the space with the text, so full start/end times keep their own row.
        val numberSize = textMeasurer.measure(
            text = sectionCount.toString(),
            style = LocalTextStyle.current.copy(
                fontSize = axisTypography.numberSizeSp.sp,
                lineHeight = axisTypography.numberLineHeightSp.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        ).size
        val timeSize = textMeasurer.measure(
            text = "23:59",
            style = LocalTextStyle.current.copy(
                fontSize = axisTypography.timeSizeSp.sp,
                lineHeight = axisTypography.timeLineHeightSp.sp,
            ),
        ).size
        val axisContentWidth = maxOf(numberSize.width, if (config.showFullTimeAxis) timeSize.width else 0)
        val axisContentHeight = numberSize.height + if (config.showFullTimeAxis) timeSize.height * 2 else 0
        val timeAxisWidth = maxOf(metrics.timeAxisWidth, with(density) { axisContentWidth.toDp() } + 8.dp)
        val cellHeight = maxOf(metrics.cellHeight, with(density) { axisContentHeight.toDp() } + 4.dp)
        val columnWidth = ((maxWidth - timeAxisWidth) / dayColumns.size).coerceAtLeast(1.dp)
        // 日期栏按系统字号自然增高；不通过压缩字号适配固定容器。
        val weekdayFontSp = axisStyle.weekdayFontSp
        val dateFontSp = axisStyle.dateFontSp
        val dateStyle = LocalTextStyle.current.copy(fontSize = (dateFontSp - 1).sp, fontWeight = FontWeight.Bold)
        val dateWidth = dates.values.maxOfOrNull {
            textMeasurer.measure(TimetableCalendar.formatMonthDay(it), style = dateStyle).size.width
        } ?: 0
        // Compact calendar headers show the day and put its month in the axis corner.
        // No date may wrap halfway through a number at narrow widths or large fonts.
        val compactDates = with(density) { dateWidth.toDp() } + 8.dp > columnWidth
        val shortWeekdays = with(density) {
            textMeasurer.measure(
                "周一", style = LocalTextStyle.current.copy(fontSize = weekdayFontSp.sp),
            ).size.width.toDp()
        } > columnWidth
        val months = dates.values.filterNotNull().map { it.monthValue }.distinct()
        Column(Modifier.fillMaxSize()) {
            // 星期和时间轴使用稳定主题底色，任意壁纸下默认文字仍清晰。
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Box(
                    Modifier.width(timeAxisWidth).heightIn(min = metrics.headerHeight).padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (compactDates) {
                        Text(
                            text = months.joinToString("\n") { "${it}月" },
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                dayColumns.forEach { day ->
                    val isToday = TimetableCalendar.isToday(startDate, week, day, today)
                    Column(
                        modifier = Modifier
                            .width(columnWidth)
                            .heightIn(min = metrics.headerHeight)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = dayNames.getValue(day).let { if (shortWeekdays) it.removePrefix("周") else it },
                            softWrap = false,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            fontSize = weekdayFontSp.sp,
                            lineHeight = (weekdayFontSp * 1.25f).sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) MaterialTheme.colorScheme.action
                            else axisTextColor(
                                axisStyle.weekdayColor,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        // Apple Calendar 风格：今天日期用实心胶囊徽章标识，替代整列底色。
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                )
                                .padding(horizontal = if (compactDates) 0.dp else 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (compactDates) dates[day]?.dayOfMonth?.toString().orEmpty()
                                else TimetableCalendar.formatMonthDay(dates[day]),
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.testTag("timetable_date_$day").semantics {
                                    contentDescription = dates[day]?.let {
                                        "${it.monthValue}月${it.dayOfMonth}日，${dayNames.getValue(day)}"
                                    }.orEmpty()
                                },
                                textAlign = TextAlign.Center,
                                fontSize = (dateFontSp - 1).sp,
                                lineHeight = ((dateFontSp - 1) * 1.2f).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                else axisTextColor(
                                    axisStyle.dateColor,
                                    MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(verticalScroll),
            ) {
                Row(
                    Modifier
                        .height(cellHeight * sectionCount)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = TIMETABLE_GRID_OVERLAY_ALPHA)),
                ) {
                    // 左侧节次标号 + 起止时间（两行，避免截断）。
                    Column(
                        Modifier
                            .width(timeAxisWidth)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        val axisLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
                        repeat(sectionCount) { index ->
                            Box(
                                Modifier
                                    .height(cellHeight)
                                    .fillMaxWidth()
                                    .testTag("timetable_axis_cell_${index + 1}")
                                    .drawBehind {
                                        if (showGrid) {
                                            drawLine(
                                                color = axisLineColor,
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 0.5.dp.toPx(),
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
                                        modifier = Modifier.testTag("timetable_axis_number_${index + 1}"),
                                        softWrap = false,
                                        maxLines = 1,
                                        fontSize = axisTypography.numberSizeSp.sp,
                                        lineHeight = axisTypography.numberLineHeightSp.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = axisTextColor(
                                            axisStyle.periodColor,
                                            MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                    if (config.showFullTimeAxis) {
                                        val parts = TimeTableConfig.timeOf(index + 1).split("-")
                                        Text(
                                            text = parts.getOrNull(0) ?: "",
                                            modifier = Modifier.testTag("timetable_axis_start_${index + 1}"),
                                            softWrap = false,
                                            maxLines = 1,
                                            fontSize = axisTypography.timeSizeSp.sp,
                                            lineHeight = axisTypography.timeLineHeightSp.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = axisTextColor(
                                                axisStyle.timeColor,
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                        Text(
                                            text = parts.getOrNull(1) ?: "",
                                            modifier = Modifier.testTag("timetable_axis_end_${index + 1}"),
                                            softWrap = false,
                                            maxLines = 1,
                                            fontSize = axisTypography.timeSizeSp.sp,
                                            lineHeight = axisTypography.timeLineHeightSp.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = axisTextColor(
                                                axisStyle.timeColor,
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .width(columnWidth * dayColumns.size)
                            .height(cellHeight * sectionCount),
                    ) {
                        if (showGrid) {
                            val horizontalLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
                            val verticalLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                            Canvas(Modifier.fillMaxSize()) {
                                val strokeWidth = 0.5.dp.toPx()
                                for (index in 1..sectionCount) {
                                    val y = cellHeight.toPx() * index
                                    drawLine(
                                        color = horizontalLineColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                    )
                                }
                                for (index in 1..dayColumns.size) {
                                    val x = columnWidth.toPx() * index
                                    drawLine(
                                        color = verticalLineColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = strokeWidth,
                                    )
                                }
                            }
                        }
                        WeekGridTapLayer(
                            dayColumns = dayColumns,
                            layout = layout,
                            columnWidth = columnWidth,
                            cellHeight = cellHeight,
                            sectionCount = sectionCount,
                            onCellClick = onCellClick,
                        )
                        Row(Modifier.fillMaxSize()) {
                            dayColumns.forEach { day ->
                                DayColumn(
                                    dayOfWeek = day,
                                    items = layout[day].orEmpty(),
                                    skippedCourseIds = skippedCourseIds,
                                    showGrid = false,
                                    layoutConfig = TimetableLayoutConfig(cellHeight.value),
                                    cellHeight = cellHeight,
                                    config = config,
                                    onCourseClick = onCourseClick,
                                    modifier = Modifier.width(columnWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单一空白网格命中层；课程卡片位于其后，仍优先处理自身点击。 */
@Composable
private fun WeekGridTapLayer(
    dayColumns: List<Int>,
    layout: Map<Int, List<TimetableLayoutItem>>,
    columnWidth: Dp,
    cellHeight: Dp,
    sectionCount: Int,
    onCellClick: (dayOfWeek: Int, startSection: Int) -> Unit,
) {
    val density = LocalDensity.current
    val columnWidthPx = with(density) { columnWidth.toPx() }
    val cellHeightPx = with(density) { cellHeight.toPx() }
    val occupied = remember(layout, dayColumns, sectionCount) {
        occupiedTimetableSections(layout, dayColumns, sectionCount)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(occupied, columnWidthPx, cellHeightPx) {
                detectTapGestures { position ->
                    val hit = hitTestTimetableCell(
                        xPx = position.x,
                        yPx = position.y,
                        metrics = TimetableGridMetrics(
                            dayColumns = dayColumns,
                            columnWidthPx = columnWidthPx,
                            sectionHeightPx = cellHeightPx,
                            sectionCount = sectionCount,
                        ),
                    ) ?: return@detectTapGestures
                    val columnIndex = dayColumns.indexOf(hit.dayOfWeek)
                    if (columnIndex >= 0 && !occupied[columnIndex][hit.startSection - 1]) {
                        onCellClick(hit.dayOfWeek, hit.startSection)
                    }
                }
            },
    )
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
    cellHeight: Dp,
    config: TimetableConfig,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.height(cellHeight * TimeTableConfig.sectionCount),
    ) {
        val narrowCard = maxWidth < 86.dp
        // 渲染顺序：先画 ghost（非本周淡化，独立布局），再画本周课程覆盖其上。
        val activeItems = items.filter { it.isCurrentWeek }
        val ghostItems = items.filter { !it.isCurrentWeek }

        // 网格只作为 Canvas 视觉层：不新增 padding/size，也不改变课程 Box 的约束。
        if (showGrid) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 0.5.dp.toPx()
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
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .width(slotWidth)
                    .height(renderHeight)
                    .padding(1.dp)
                    .tutorialTarget(TutorialTargetKey.TIMETABLE_COURSE_CARD),
            ) {
                CourseCard(
                    course = course,
                    isCurrentWeek = item.isCurrentWeek,
                    isSkipped = course.id in skippedCourseIds,
                    // 窄列降低备注密度；保留配置字号与地点/教师字段，卡片内有界省略。
                    showTeacher = config.showTeacher,
                    showLocation = config.showLocation,
                    showNote = config.showNote && !narrowCard,
                    textSize = config.textSize,
                    cornerRadius = config.cornerRadius,
                    cardAlpha = config.courseAlpha,
                    textHorizontalAlignment = config.textHorizontalAlignment,
                    textVerticalAlignment = config.textVerticalAlignment,
                    compact = narrowCard,
                    onClick = { onCourseClick(course) },
                )
            }
        }
    }
}
