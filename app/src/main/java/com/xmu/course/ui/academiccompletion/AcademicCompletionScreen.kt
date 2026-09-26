package com.xmu.course.ui.academiccompletion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.CompletedCourseOutsidePlan
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.AppListDivider
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget

/**
 * 培养方案详情页：方案完成概览 + 方案外已结课记录。
 *
 * 页面不访问网络、不读取认证信息；快照唯一生产通道是用户的「刷新学业数据」。
 * 展示纪律：来源确认值、本地补充值、含本地确认合计三者永远分开呈现。
 * 在修课程与学分确认已迁移至「本学期」页（学业模块）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AcademicCompletionScreenContent(
    viewModel: AcademicCompletionViewModel,
    onBack: () -> Unit,
    onOpenSemester: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(uiState.notice) {
        val message = uiState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNotice()
    }

    Scaffold(
        modifier = modifier.testTag("academic_completion_screen"),
        topBar = {
            TopAppBar(
                title = { Text("培养方案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TutorialToolbarAction() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState.storeState) {
            is AcademicCompletionStore.State.Empty -> AppEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag("academic_completion_empty"),
                title = "本机还没有培养方案数据",
                description = "回到「学业」页点击刷新，即可从教务获取培养方案进度。",
                icon = Icons.Filled.School,
            )

            is AcademicCompletionStore.State.StorageFailure -> AppEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag("academic_completion_storage_failure"),
                title = "本机快照读取失败",
                description = state.message,
                icon = Icons.Filled.School,
                actionLabel = "删除损坏快照",
                onAction = { showClearConfirm = true },
            )

            is AcademicCompletionStore.State.Loaded -> SnapshotContent(
                snapshot = state.snapshot,
                uiState = uiState,
                innerPadding = innerPadding,
                onOpenSemester = onOpenSemester,
                onClearRequested = { showClearConfirm = true },
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("删除本机学业快照") },
            text = {
                Text("将删除本机保存的快照与全部本地学分确认。不影响教务系统，也不会清除任何登录状态。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearLocalData()
                    },
                    modifier = Modifier.testTag("academic_completion_clear_confirm"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                    },
                    modifier = Modifier.testTag("academic_completion_clear_cancel"),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SnapshotContent(
    snapshot: AcademicCompletionSnapshot,
    uiState: com.xmu.course.ui.academiccompletion.AcademicCompletionUiState,
    innerPadding: PaddingValues,
    onOpenSemester: () -> Unit,
    onClearRequested: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(
            start = AppSpacing.PagePadding,
            top = AppSpacing.PagePadding,
            end = AppSpacing.PagePadding,
            bottom = AppSpacing.PagePadding,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
    ) {
        item {
            PlanOverviewCard(
                snapshot = snapshot,
                onOpenSemester = onOpenSemester,
                onClear = onClearRequested,
            )
        }
        if (snapshot.completedCoursesOutsidePlan.isNotEmpty()) {
            item {
                AppSectionCard(title = "方案外已结课（${snapshot.completedCoursesOutsidePlan.size}）") {
                    Text(
                        "方案归属以学校数据为准；这些课程是否计入本地 GPA 由你在「GPA 计算」中确认，App 不替你做决定。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    snapshot.completedCoursesOutsidePlan.forEachIndexed { index, course ->
                        if (index > 0) {
                            AppListDivider(inset = AppSpacing.Lg)
                        }
                        CompletedCourseRow(course)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanOverviewCard(
    snapshot: AcademicCompletionSnapshot,
    onOpenSemester: () -> Unit,
    onClear: () -> Unit,
) {
    AppSectionCard(
        title = "方案概览",
        modifier = Modifier.testTag("academic_completion_overview").tutorialTarget(TutorialTargetKey.PLAN_OVERVIEW),
    ) {
        Text(
            "${snapshot.plan.planName} · ${snapshot.semesterLabel}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "已获学分 ${snapshot.plan.earnedCreditsText} / ${snapshot.plan.requiredCreditsText}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "来源已确认 · 本学期已选 ${snapshot.plan.sourceThisSemesterTotalText} 学分",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("academic_completion_source_total"),
        )
        if (snapshot.hasLocalSupplement) {
            Text(
                "本地补充 ${snapshot.localSupplementedTotalText()} 学分",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("academic_completion_local_total"),
            )
            Text(
                "含本地确认合计 ${snapshot.combinedTotalIncludingLocalText()} 学分",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("academic_completion_combined_total"),
            )
            Text(
                "本地补充为你手动确认的值，不属于教务来源总计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 本学期入口常驻（POL-004）：教程锚点不能只在“有学分待确认”时才存在，
        // 否则无待确认课程的用户会停在一步没有目标的教程上。待确认提示只是条件前缀。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            if (snapshot.pendingManualCourses.isNotEmpty()) {
                AppStatusChip(
                    label = "${snapshot.pendingManualCourses.size} 门课程学分待确认",
                    tone = StatusTone.Warning,
                    modifier = Modifier.testTag("academic_completion_pending_count"),
                )
            }
            TextButton(
                onClick = onOpenSemester,
                modifier = Modifier.testTag("academic_completion_open_semester")
                    .tutorialTarget(TutorialTargetKey.PLAN_SEMESTER),
            ) {
                Text(
                    if (snapshot.pendingManualCourses.isNotEmpty()) "去本学期确认"
                    else "查看本学期在修课程",
                )
            }
        }
        Text(
            "学校页面统计时间：${snapshot.plan.sourceSnapshotAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("academic_completion_snapshot_time"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.testTag("academic_completion_clear"),
            ) {
                Text("删除本机快照", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun CompletedCourseRow(course: CompletedCourseOutsidePlan) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(course.courseName, style = MaterialTheme.typography.bodyLarge)
            Text(
                course.termCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${course.creditsText} 学分 · 成绩 ${course.scoreText}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("academic_completion_completed_${course.courseCode}"),
        )
    }
}

@Composable
internal fun EnrolledCourseRow(
    snapshot: AcademicCompletionSnapshot,
    course: SourceCourse,
    onFillCredits: () -> Unit,
) {
    val localConfirmed = snapshot.isLocalConfirmed(course)
    val effectiveCredits = snapshot.effectiveCreditsText(course)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(course.courseName, style = MaterialTheme.typography.bodyLarge)
            val meta = listOf(course.teacherNames, course.classCode)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (course.status == com.xmu.course.data.academiccompletion.SourceXfStatus.NEEDS_MANUAL &&
                !localConfirmed
            ) {
                Text(
                    "来源暂无学分（教务系统对方案外在修课的限制）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            effectiveCredits != null && localConfirmed -> Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                Text("${effectiveCredits} 学分", style = MaterialTheme.typography.bodyLarge)
                AppStatusChip(
                    label = "本地确认",
                    tone = StatusTone.Info,
                    modifier = Modifier.testTag("academic_completion_chip_local_${course.courseCode}"),
                )
            }
            effectiveCredits != null -> Text(
                "${effectiveCredits} 学分",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.testTag("academic_completion_credits_${course.courseCode}"),
            )
            else -> TextButton(
                onClick = onFillCredits,
                modifier = Modifier.testTag("academic_completion_fill_${course.courseCode}"),
            ) { Text("填入学分") }
        }
    }
}

@Composable
internal fun CreditInputDialog(
    course: SourceCourse,
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("填入学分") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Text(course.courseName, style = MaterialTheme.typography.titleSmall)
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("学分（0 到 10，十进制）") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("academic_completion_credit_field"),
                )
                Text(
                    course.confirmationHint
                        ?: "来源暂无法提供该课程学分；填写仅保存在本机，不会修改来源事实。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("academic_completion_credit_hint"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                modifier = Modifier.testTag("academic_completion_credit_submit"),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("academic_completion_credit_cancel"),
            ) { Text("取消") }
        },
    )
}

@Composable
fun AcademicCompletionScreen(
    viewModel: AcademicCompletionViewModel,
    onBack: () -> Unit,
    onOpenSemester: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TutorialHost(tutorialId = "academic_plan", modifier = modifier.fillMaxSize()) {
        AcademicCompletionScreenContent(
            viewModel = viewModel,
            onBack = onBack,
            onOpenSemester = onOpenSemester,
            modifier = Modifier,
        )
    }
}
