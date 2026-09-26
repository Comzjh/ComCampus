package com.xmu.course.ui.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.academic.AcademicSimulationAvailability
import com.xmu.course.ui.academic.SimulationCourseSeed
import com.xmu.course.ui.academic.SimulationGpaStatus
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppListDivider
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.tutorial.TutorialAutoScroll
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 学业模拟页：自动以本机学业缓存为基线，用户只调整“模拟成绩”。
 *
 * 本页不再有 Excel/PDF 填充入口，也不会写回任何来源：
 * 真实成绩只读展示，模拟值只存在于本次会话（spec Phase Q）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradesSandboxScreenContent(
    viewModel: GradesSandboxViewModel,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenGpaSettings: () -> Unit = {},
    onOpenSemester: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val seed = state.seed
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.testTag("grades_sandbox_screen"),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("学业模拟") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("grades_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TutorialToolbarAction()
                    // 明确本页是模拟推演，不是学校结论。
                    AppStatusChip(label = "模拟", tone = StatusTone.Info)
                },
            )
        },
    ) { innerPadding ->
        TutorialAutoScroll(scrollState, "academic_simulation", innerPadding.calculateTopPadding())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
        ) {
            Text(
                "调整本学期或未来课程的成绩，看看 GPA 和目标差距会怎么变化。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "模拟只在手机上推演，不会修改你的真实成绩。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("grades_local_only_note"),
            )
            // 刻度与参与规则只在页顶说明一次，避免每行重复长句。
            Text(
                "拖动滑块设分数（60–100），或直接填等级；没设置过的课程不参与计算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("grades_scale_note"),
            )

            // BUG-05：只有“本机确实没有任何学业快照”才算空态；部分数据不再说“没数据”。
            if (seed.availability is AcademicSimulationAvailability.Empty) {
                AppEmptyState(
                    title = "还没有学业数据",
                    description = "在「数据与来源」点「刷新学业数据」后，模拟会自动载入已修课程和本学期课程，不用手填。",
                    actionLabel = "刷新学业数据",
                    onAction = onRefresh,
                    modifier = Modifier.testTag("grades_seed_empty"),
                )
                OutlinedButton(
                    onClick = viewModel::addCourse,
                    modifier = Modifier.testTag("grades_add_course_without_data"),
                ) { Text("手动添加模拟课程") }
                return@Column
            }

            BaselineCard(
                state = state,
                onOpenGpaSettings = onOpenGpaSettings,
                onToggleManualBaseline = viewModel::setManualBaselineEnabled,
                onManualCurrentGpaChange = viewModel::updateManualCurrentGpa,
                onManualCompletedCreditsChange = viewModel::updateManualCompletedCredits,
            )

            if (seed.simlatableCourses.isNotEmpty() ||
                seed.alreadyGradedCourses.isNotEmpty() ||
                seed.unresolvedCourses.isNotEmpty()) {
                AppSectionCard(
                    title = "本学期" + (seed.semesterLabel?.let { " · $it" } ?: ""),
                    modifier = Modifier.testTag("grades_semester_section").tutorialTarget(TutorialTargetKey.SIMULATION_SEMESTER),
                ) {
                    val graded = seed.alreadyGradedCourses
                    val simlatable = seed.simlatableCourses
                    val unresolved = seed.unresolvedCourses
                    graded.forEachIndexed { index, course ->
                        if (index > 0) AppListDivider(inset = 0.dp)
                        GradedCourseRow(course)
                    }
                    if (graded.isNotEmpty() && (simlatable.isNotEmpty() || unresolved.isNotEmpty())) {
                        AppListDivider(inset = 0.dp)
                    }
                    simlatable.forEachIndexed { index, course ->
                        val row = state.courses.firstOrNull { it.key == SEEDED_COURSE_PREFIX + course.courseCode }
                        if (index > 0) AppListDivider(inset = 0.dp)
                        if (row != null) {
                            SimulatedCourseRow(
                                course = row,
                                editableIdentity = false,
                                onScoreChange = { viewModel.updateCourseScore(row.id, it) },
                                onGradeChange = { viewModel.updateCourseGrade(row.id, it) },
                                onClear = { viewModel.clearCourseSimulation(row.id) },
                                onRemove = null,
                            )
                        }
                    }
                    if (simlatable.isNotEmpty() && unresolved.isNotEmpty()) {
                        AppListDivider(inset = 0.dp)
                    }
                    unresolved.forEachIndexed { index, course ->
                        if (index > 0) AppListDivider(inset = 0.dp)
                        UnresolvedCourseRow(course, onOpenSemester = onOpenSemester)
                    }
                }
            }

            if (seed.availability.canSimulate && seed.currentSemesterCourses.isEmpty()) {
                AppSectionCard(
                    title = "本学期" + (seed.semesterLabel?.let { " · $it" } ?: ""),
                    modifier = Modifier.testTag("grades_semester_empty"),
                ) {
                    Text(
                        "本学期暂无可模拟课程；在「数据与来源」刷新学业完成数据后会自动载入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("grades_semester_empty_text"),
                    )
                }
            }

            AppSectionCard(
                title = "预计结果",
                modifier = Modifier.testTag("grades_result_section").tutorialTarget(TutorialTargetKey.SIMULATION_RESULT),
            ) {
                state.errorMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("grades_error"),
                    )
                }
                if (state.result == null && state.errorMessage == null) {
                    Text(
                        "给任意一门课填好分数与学分，这里会实时给出预计 GPA。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("grades_result_placeholder"),
                    )
                }
                state.result?.let { result ->
                    AppListDivider(inset = 0.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grades_result"),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                    ) {
                        Text("预计 GPA：${"%.2f".format(Locale.US, result.gpa)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "参与 " + result.simulatedCourseCount + " 门",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("grades_result_participating"),
                        )
                        if (state.skippedCount > 0) {
                            Text(
                                "另有 ${state.skippedCount} 门未设置模拟成绩，未参与计算。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("grades_result_skipped"),
                            )
                        }
                        Text("模拟后总学分：${"%.2f".format(Locale.US, result.totalCredits)}")
                        result.targetGap?.let { gap ->
                            Text(
                                if (gap <= 0.0) "已达到目标，还高出 ${"%.2f".format(Locale.US, -gap)}。"
                                else "距离目标还差 ${"%.2f".format(Locale.US, gap)}。",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("grades_result_gap"),
                            )
                        }
                        Text(
                            "以上为模拟值，不会修改真实成绩。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = viewModel::simulate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grades_simulate"),
                ) { Text("计算模拟 GPA") }
                // 重置只影响模拟值，降为文字按钮，避免与主操作同权重。
                TextButton(
                    onClick = viewModel::resetSimulation,
                    modifier = Modifier.testTag("grades_reset"),
                ) { Text("重置模拟") }
            }

            AppSectionCard(
                title = "目标 GPA（可选）",
                modifier = Modifier
                    .testTag("grades_target_section")
                    .tutorialTarget(TutorialTargetKey.SIMULATION_TARGET_GPA),
            ) {
                OutlinedTextField(
                    value = state.targetGpa,
                    onValueChange = viewModel::updateTargetGpa,
                    label = { Text("目标 GPA") },
                    supportingText = { Text("不设目标也能直接看预计 GPA。") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grades_target_gpa"),
                )
            }

            AppSectionCard(
                title = "自定义模拟",
                modifier = Modifier.testTag("grades_custom_section").tutorialTarget(TutorialTargetKey.SIMULATION_CUSTOM),
                action = {
                    TextButton(onClick = viewModel::addCourse, modifier = Modifier.testTag("grades_add_course")) {
                        Text("添加未来课程")
                    }
                },
            ) {
                val customRows = state.courses.filterNot { it.seeded }
                if (customRows.isEmpty()) {
                    Text(
                        "还没添加未来课程。想提前算一门课的分数，可以加在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("grades_empty_courses"),
                    )
                } else {
                    customRows.forEachIndexed { index, row ->
                        if (index > 0) AppListDivider(inset = 0.dp)
                        SimulatedCourseRow(
                            course = row,
                            editableIdentity = true,
                            onNameChange = { viewModel.updateCourseName(row.id, it) },
                            onCreditsChange = { viewModel.updateCourseCredits(row.id, it) },
                            onScoreChange = { viewModel.updateCourseScore(row.id, it) },
                            onGradeChange = { viewModel.updateCourseGrade(row.id, it) },
                            onClear = { viewModel.clearCourseSimulation(row.id) },
                            onRemove = { viewModel.removeCourse(row.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 当前基线卡：默认全部来自已同步数据，手动基线收在二级开关里。 */
@Composable
private fun BaselineCard(
    state: GradesSandboxState,
    onOpenGpaSettings: () -> Unit,
    onToggleManualBaseline: (Boolean) -> Unit,
    onManualCurrentGpaChange: (String) -> Unit,
    onManualCompletedCreditsChange: (String) -> Unit,
) {
    val baseline = state.seed.baseline
    AppSectionCard(
        title = "当前基线",
        modifier = Modifier.testTag("grades_baseline_card").tutorialTarget(TutorialTargetKey.SIMULATION_BASELINE),
    ) {
        if (state.manualBaselineEnabled) {
            OutlinedTextField(
                value = state.manualCurrentGpa,
                onValueChange = onManualCurrentGpaChange,
                label = { Text("当前 GPA") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grades_manual_gpa"),
            )
            OutlinedTextField(
                value = state.manualCompletedCredits,
                onValueChange = onManualCompletedCreditsChange,
                label = { Text("已修学分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grades_manual_credits"),
            )
            Text(
                "手动值只影响本次模拟，不会修改学业数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            when (baseline?.gpaStatus) {
                SimulationGpaStatus.COMPUTED -> {
                    Text("本地计算 GPA", style = MaterialTheme.typography.labelMedium)
                    Text(
                        baseline.gpaText.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("grades_baseline_gpa"),
                    )
                }
                SimulationGpaStatus.NEEDS_CONFIRMATION -> {
                    Text(
                        "需要先确认方案外课程",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("grades_baseline_needs_confirmation"),
                    )
                    TextButton(onClick = onOpenGpaSettings, modifier = Modifier.testTag("grades_open_gpa_settings")) {
                        Text("选择计入方式")
                    }
                }
                SimulationGpaStatus.INSUFFICIENT -> Text(
                    "现有成绩还没有可用绩点，暂时算不出起点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("grades_baseline_insufficient"),
                )
                else -> Text(
                    // BUG-05：缺口描述取自唯一判据，不再在页面里凑话术。
                    state.seed.availability.guidance ?: "暂无可载入的基线数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("grades_availability_guidance"),
                )
            }
            Text(
                "已修学分：" + (baseline?.earnedCreditsText ?: "暂无"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "数据来自本机学业缓存，可离线使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("grades_source_indicator"),
            )
            baseline?.dataUpdatedText?.let { updated ->
                Text(
                    "更新于 $updated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = { onToggleManualBaseline(!state.manualBaselineEnabled) },
            modifier = Modifier.testTag("grades_toggle_manual_baseline"),
        ) {
            Text(if (state.manualBaselineEnabled) "使用本机数据作为基线" else "手动调整模拟基线")
        }
        if (state.storageUnavailable) {
            Text(
                "本机学业缓存读取失败；可重新刷新，或在「数据管理」清除损坏缓存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("grades_storage_failure"),
            )
        }
    }
}

/** 已有真实成绩：只读展示，已计入基线，避免重复计算。 */
@Composable
private fun GradedCourseRow(course: SimulationCourseSeed) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Md)
            .testTag("grades_graded_" + course.courseCode),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        Text(course.courseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        AppStatusChip("已有成绩 " + course.realGradeText.orEmpty(), tone = StatusTone.Neutral)
        Text(
            course.creditsText?.let { it + " 学分" } ?: "学分待确认",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 学分待确认：显式引导去本学期页补录，不自动猜值。 */
@Composable
private fun UnresolvedCourseRow(course: SimulationCourseSeed, onOpenSemester: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Md)
            .testTag("grades_unresolved_" + course.courseCode),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            Text(course.courseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            AppStatusChip("学分待确认", tone = StatusTone.Warning)
        }
        TextButton(onClick = onOpenSemester, modifier = Modifier.testTag("grades_open_semester")) {
            Text("去填入学分")
        }
    }
}

@Composable
private fun SimulatedCourseRow(
    course: SimulatedCourseInputState,
    editableIdentity: Boolean,
    onScoreChange: (Int) -> Unit,
    onGradeChange: (String) -> Unit,
    onClear: () -> Unit,
    onRemove: (() -> Unit)?,
    onNameChange: (String) -> Unit = {},
    onCreditsChange: (String) -> Unit = {},
) {
    // 分组行：不再嵌套 Card，卡内层级靠分隔线与间距表达（分数滑块 + 等级输入）。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Md)
            .testTag("grades_course_" + course.id),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (editableIdentity) {
                OutlinedTextField(
                    value = course.name,
                    onValueChange = onNameChange,
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("grades_course_name_" + course.id),
                )
            } else {
                Text(
                    course.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("grades_course_label_" + course.id),
                )
            }
            if (course.outsidePlan) {
                AppStatusChip("方案外", tone = StatusTone.Warning)
            }
            if (!course.participates) {
                AppStatusChip("未设置", tone = StatusTone.Neutral)
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.testTag("grades_remove_course_" + course.id)) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除这门模拟课程")
                }
            }
        }
        if (editableIdentity) {
            OutlinedTextField(
                value = course.credits,
                onValueChange = onCreditsChange,
                label = { Text("课程学分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grades_course_credits_" + course.id),
            )
        } else {
            Text(
                "学分 " + course.credits,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            Slider(
                value = course.score.toFloat(),
                onValueChange = { onScoreChange(it.roundToInt()) },
                valueRange = 60f..100f,
                steps = 39,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = course.name.ifBlank { "这门课程" } + "的模拟分数滑块"
                    }
                    .testTag("grades_course_score_" + course.id),
            )
            Text(
                text = if (course.participates) gradeSummary(course) else "-",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .width(72.dp)
                    .testTag("grades_course_score_label_" + course.id),
            )
        }
        OutlinedTextField(
            value = course.grade,
            onValueChange = onGradeChange,
            label = { Text("等级（填了覆盖分数）") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("grades_course_grade_" + course.id),
        )
        if (course.participates) {
            TextButton(onClick = onClear, modifier = Modifier.testTag("grades_course_clear_" + course.id)) {
                Text("设为不参与")
            }
        }
    }
}

private fun gradeSummary(course: SimulatedCourseInputState): String =
    if (course.grade.isNotBlank()) course.grade.trim().uppercase(Locale.US)
    else "${course.score} 分"

@Composable
fun GradesSandboxScreen(
    viewModel: GradesSandboxViewModel,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenGpaSettings: () -> Unit = {},
    onOpenSemester: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TutorialHost(tutorialId = "academic_simulation", modifier = modifier.fillMaxSize()) {
        GradesSandboxScreenContent(
            viewModel = viewModel,
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenGpaSettings = onOpenGpaSettings,
            onOpenSemester = onOpenSemester,
            modifier = Modifier,
        )
    }
}
