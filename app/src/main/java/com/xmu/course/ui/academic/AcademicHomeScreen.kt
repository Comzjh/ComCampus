package com.xmu.course.ui.academic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppLargeTitleHeader
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.tutorial.TutorialAutoScroll
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget
import kotlin.math.roundToInt

/**
 * 学业首页：学业概览与学业模拟优先，维护动作（刷新）降级到「数据与来源」。
 *
 * 视觉顺序（spec Phase AD）：概览 → 学业模拟 → GPA → 本学期 → 历史成绩 → 培养方案 → 数据与来源。
 * 页面只消费本地聚合快照：零协议术语、零自动访问，刷新仍需用户点击。
 */
@Composable
private fun AcademicHomeScreenContent(
    viewModel: AcademicHomeViewModel,
    onRefresh: () -> Unit,
    onOpenSemester: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGpa: () -> Unit,
    onOpenSimulation: () -> Unit,
    onOpenDataSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var showSourceDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    TutorialAutoScroll(scrollState, "academic_home")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(AppSpacing.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
    ) {
        AppLargeTitleHeader(title = "学业", trailingContent = { TutorialToolbarAction() })

        if (state.planStorageFailed || state.gradeStorageFailed) {
            AppSectionCard(title = "本机缓存异常") {
                Text(
                    "部分学业数据本机读取失败；到「数据与来源」重新刷新，或在「数据管理」清除损坏缓存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("academic_storage_failure"),
                )
            }
        }

        val overview = state.overview
        if (overview != null) {
            AcademicOverviewCard(overview, state.planFetchedAtText)
        }
        if (overview == null && state.isFreshEmpty) {
            AppEmptyState(
                title = "还没有学业数据",
                description = "手动刷新成绩与培养方案，数据只保存在本机。",
                icon = Icons.Outlined.School,
                actionLabel = "刷新学业数据",
                onAction = onRefresh,
                modifier = Modifier.testTag("academic_home_empty"),
                compact = true,
                primaryAction = true,
            )
            TextButton(
                onClick = { showSourceDialog = true },
                modifier = Modifier.testTag("academic_home_about_sources"),
            ) { Text("了解数据来源", style = MaterialTheme.typography.bodySmall) }
        } else if (state.gradeSnapshotLoaded && state.gradeCourseCount == 0) {
            AppEmptyState(
                title = "暂无可显示的成绩",
                description = "最近一次成绩查询没有返回成绩记录。有新成绩时可以手动刷新；数据只保存在这台手机。",
                icon = Icons.Outlined.School,
                actionLabel = "刷新学业数据",
                onAction = onRefresh,
                modifier = Modifier.testTag("academic_home_no_grades"),
                compact = true,
                primaryAction = true,
            )
        }

        if (state.isFreshEmpty) {
            AppGroupedSection(
                title = "学业工具",
                modifier = Modifier.testTag("academic_simulation_card")
                    .tutorialTarget(TutorialTargetKey.ACADEMIC_SIMULATION_ENTRY),
            ) {
                AppNavigationRow(
                    title = "学业模拟",
                    description = "调整成绩，预估 GPA；不会修改真实成绩",
                    onClick = onOpenSimulation,
                    modifier = Modifier.testTag("academic_open_simulation"),
                )
            }
        } else {
            AcademicGpaCard(summary = state.gpa, onOpenGpa = onOpenGpa)
            AcademicSimulationEntryCard(
                summary = state.gpa,
                enrolledCount = state.simlatableCourseCount,
                onOpenSimulation = onOpenSimulation,
            )
        }

        val snapshot = state.snapshot
        if (snapshot != null && snapshot.enrolledCourses.isNotEmpty()) {
            AppSectionCard(
                title = "本学期 · ${snapshot.semesterLabel}",
                modifier = Modifier.testTag("academic_semester_card"),
                action = {
                    TextButton(onClick = onOpenSemester, modifier = Modifier.testTag("academic_open_semester")) {
                        Text("查看全部")
                    }
                },
            ) {
                val visibleCourses = snapshot.enrolledCourses.take(3)
                visibleCourses.forEach { course ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.Xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                    ) {
                        Text(
                            course.courseName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!course.inPlan) {
                            AppStatusChip("方案外", tone = StatusTone.Warning)
                        }
                        Text(
                            snapshot.effectiveCreditsText(course)?.let { "$it 学分" } ?: "学分待确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val hiddenCourses = snapshot.enrolledCourses.size - visibleCourses.size
                if (hiddenCourses > 0) {
                    Text(
                        "另有 $hiddenCourses 门，点「查看全部」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("academic_semester_more"),
                    )
                }
            }
        }

        if (state.historySemesters.isNotEmpty() || state.gradeCourseCount > 0) {
            AcademicHistoryPreviewCard(
                semesters = state.historySemesters,
                hasGrades = state.gradeCourseCount > 0,
                onOpenHistory = onOpenHistory,
            )
        }

        overview?.let { plan ->
            AppSectionCard(
                title = "培养方案",
                modifier = Modifier.testTag("academic_plan_card"),
                action = {
                    TextButton(onClick = onOpenPlan, modifier = Modifier.testTag("academic_open_plan")) {
                        Text("详情")
                    }
                },
            ) {
                Text(plan.planName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "修读要求与方案外课程在详情页确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AppGroupedSection(
            title = "数据与设置",
            modifier = Modifier.testTag("academic_data_sources_card").tutorialTarget(TutorialTargetKey.ACADEMIC_DATA_SOURCES),
        ) {
            AppNavigationRow(
                title = "数据与来源",
                description = "刷新学业数据、查看学校原始页面与本机数据管理",
                onClick = onOpenDataSources,
                modifier = Modifier.testTag("academic_open_data_sources"),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            AppNavigationRow(
                title = "GPA 计算设置",
                description = "方案外课程是否计入本地 GPA",
                onClick = onOpenGpa,
                modifier = Modifier.testTag("academic_open_gpa_settings"),
            )
        }

        state.gradeRefreshedAtText?.let { refreshedAt ->
            Text(
                "上次刷新 $refreshedAt · 数据只保存在本机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("academic_footer_provenance"),
            )
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("数据来源说明") },
            text = {
                Text(
                    "学业数据来自学校教务页面，由你在本机主动刷新后保存；" +
                        "App 不会后台自动访问教务系统，也不会上传你的学业数据。" +
                        "登录信息只保留在学校页面内，ComCampus 不读取、不保存你的账号凭据。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun AcademicSimulationEntryCard(
    summary: AcademicGpaSummary,
    enrolledCount: Int,
    onOpenSimulation: () -> Unit,
) {
    AppSectionCard(
        title = "学业模拟",
        modifier = Modifier.testTag("academic_simulation_card").tutorialTarget(TutorialTargetKey.ACADEMIC_SIMULATION_ENTRY),
        action = {
            TextButton(onClick = onOpenSimulation, modifier = Modifier.testTag("academic_open_simulation")) {
                Icon(Icons.AutoMirrored.Outlined.TrendingFlat, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("打开")
            }
        },
    ) {
        Text(
            "调整成绩，看看 GPA 和目标差距会怎么变化。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("academic_simulation_hint"),
        )
        if (enrolledCount > 0) {
            Text(
                "本学期 $enrolledCount 门在修课程可参与模拟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "模拟结果不会修改真实成绩。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.tone == AcademicGpaTone.NEEDS_CONFIRMATION) {
            AppStatusChip("有方案外课程待确认", tone = StatusTone.Info)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AcademicOverviewCard(overview: AcademicOverviewRow, planFetchedAtText: String?) {
    AppSectionCard(
        title = "学业概览",
        modifier = Modifier.testTag("academic_overview_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
        ) {
            AcademicProgressRing(fraction = overview.progressFraction)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xxs),
            ) {
                Text("已修学分", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${overview.earnedCreditsText} / ${overview.requiredCreditsText}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("academic_credits_row"),
                )
                overview.remainingCreditsText?.let {
                    Text(
                        "剩余 $it 学分达标",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            Text(
                "本学期在修 ${overview.enrolledCount} 门",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().testTag("academic_enrolled_count"),
            )
            if (overview.outsidePlanCompletedCount > 0 || overview.pendingManualCount > 0) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("academic_overview_status_chips"),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                ) {
                    if (overview.outsidePlanCompletedCount > 0) {
                        AppStatusChip("方案外 ${overview.outsidePlanCompletedCount} 门", tone = StatusTone.Warning)
                    }
                    if (overview.pendingManualCount > 0) {
                        AppStatusChip("学分待确认 ${overview.pendingManualCount}", tone = StatusTone.Info)
                    }
                }
            }
        }
        Text(
            "学校页面统计时间 ${overview.sourceSnapshotAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.Xs),
        )
        Text(
            "本机获取时间 ${planFetchedAtText ?: "未记录"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AcademicProgressRing(fraction: Float?) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val stroke = 8.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val value = fraction?.coerceIn(0f, 1f) ?: 0f
            if (value > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * value,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AcademicGpaCard(summary: AcademicGpaSummary, onOpenGpa: () -> Unit) {
    AppSectionCard(
        title = "GPA",
        modifier = Modifier.testTag("academic_gpa_card"),
        action = {
            TextButton(onClick = onOpenGpa) { Text("设置") }
        },
    ) {
        when (summary.tone) {
            AcademicGpaTone.NO_DATA -> Text(
                "刷新学业数据后可本地计算 GPA",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AcademicGpaTone.INSUFFICIENT -> Text(
                "现有成绩暂无可用绩点记录，尚不能计算",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("academic_gpa_insufficient"),
            )
            AcademicGpaTone.NEEDS_CONFIRMATION -> Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
                Text(
                    "需要确认 ${summary.candidateCount} 门方案外课程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("academic_gpa_needs_confirmation"),
                )
                Text(
                    "学校培养方案归属可能存在延迟或异常，请先选择这些课程是否参与本地 GPA 计算。确认前不给出任何 GPA 结论。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AcademicGpaTone.COMPUTED -> Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xxs)) {
                Text("本地计算 GPA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    summary.valueText.orEmpty(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("academic_gpa_value"),
                )
                Text(
                    buildString {
                        append("按逐课绩点加权 · 计入 ${summary.countedCourses} 门 · ${summary.countedCreditsText.orEmpty()} 学分")
                        if (summary.pointFreeCourses > 0) append(" · 合格制不计绩点 ${summary.pointFreeCourses} 门")
                        if (summary.excludedByPolicy > 0) append(" · 按你的选择不计入 ${summary.excludedByPolicy} 门")
                        if (summary.malformedCourses > 0) append(" · 数据异常未参与 ${summary.malformedCourses} 门")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "本数值为设备上的估算，不是教务系统给出的 GPA。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!summary.planSynced) {
                    AppStatusChip("培养方案未刷新 · 方案归属未核对", tone = StatusTone.Warning)
                }
            }
        }
    }
}

@Composable
private fun AcademicHistoryPreviewCard(
    semesters: List<AcademicSemesterRow>,
    hasGrades: Boolean,
    onOpenHistory: () -> Unit,
) {
    AppSectionCard(
        title = "历史成绩",
        modifier = Modifier.testTag("academic_history_card"),
        action = {
            if (hasGrades) {
                TextButton(onClick = onOpenHistory, modifier = Modifier.testTag("academic_open_history")) {
                    Text("查看全部")
                }
            }
        },
    ) {
        if (semesters.isEmpty()) {
            Text(
                "刷新后自动补齐各学期成绩",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppSectionCard
        }
        semesters.take(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.Xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                Text(row.semesterDisplay, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (row.hasOutsidePlanCourse) {
                    AppStatusChip("方案外", tone = StatusTone.Warning)
                }
                Text(
                    "${row.courseCount} 门 · ${row.creditsText} 学分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AcademicHomeScreen(
    viewModel: AcademicHomeViewModel,
    onRefresh: () -> Unit,
    onOpenSemester: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGpa: () -> Unit,
    onOpenSimulation: () -> Unit,
    onOpenDataSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TutorialHost(tutorialId = "academic_home", modifier = modifier.fillMaxSize()) {
        AcademicHomeScreenContent(
            viewModel = viewModel,
            onRefresh = onRefresh,
            onOpenSemester = onOpenSemester,
            onOpenPlan = onOpenPlan,
            onOpenHistory = onOpenHistory,
            onOpenGpa = onOpenGpa,
            onOpenSimulation = onOpenSimulation,
            onOpenDataSources = onOpenDataSources,
            modifier = Modifier,
        )
    }
}
