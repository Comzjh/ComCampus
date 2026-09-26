package com.xmu.course.ui.academic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing

/**
 * GPA 计算设置：三态策略由用户裁决，只影响本地派生值。
 *
 * 官方成绩、学分、方案归属绝不被此页修改，也不写回学校；
 * 未确认存在方案外候选课程时页面明确「不出数」，不给默认结论。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicGpaScreen(
    viewModel: AcademicGpaViewModel,
    onBack: () -> Unit,
    onOpenSandbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.testTag("academic_gpa_screen"),
        topBar = {
            TopAppBar(
                title = { Text("GPA 计算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
        ) {
            item {
                AppSectionCard(title = "本地计算 GPA", modifier = Modifier.testTag("academic_gpa_result_card")) {
                    when (uiState.summary.tone) {
                        AcademicGpaTone.NO_DATA -> Text(
                            "尚无成绩缓存；刷新学业数据后可本地计算。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AcademicGpaTone.INSUFFICIENT -> Text(
                            "现有成绩暂无可用绩点记录，尚不能计算。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AcademicGpaTone.NEEDS_CONFIRMATION -> Column(
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                        ) {
                            Text(
                                "待确认，暂不出数",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("academic_gpa_pending"),
                            )
                            Text(
                                "发现 ${uiState.summary.candidateCount} 门方案外课程。请在下方选择计算口径后再生成本地 GPA。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AcademicGpaTone.COMPUTED -> Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xxs)) {
                            Text(
                                uiState.summary.valueText.orEmpty(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("academic_gpa_computed"),
                            )
                            Text(
                                buildString {
                                    append("计入 ${uiState.summary.countedCourses} 门 · ${uiState.summary.countedCreditsText.orEmpty()} 学分")
                                    if (uiState.summary.pointFreeCourses > 0) append(" · 合格制不计绩点 ${uiState.summary.pointFreeCourses} 门")
                                    if (uiState.summary.excludedByPolicy > 0) append(" · 按你的选择不计入 ${uiState.summary.excludedByPolicy} 门")
                                    if (uiState.summary.malformedCourses > 0) append(" · 数据异常未参与 ${uiState.summary.malformedCourses} 门")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!uiState.summary.planSynced) {
                                AppStatusChip("培养方案未刷新 · 方案归属未核对", tone = StatusTone.Warning)
                            }
                        }
                    }
                }
            }
            item {
                AppSectionCard(
                    title = "方案外课程是否计入",
                    modifier = Modifier.testTag("academic_gpa_policy_card"),
                ) {
                    Text(
                        "学校培养方案归属可能存在延迟或异常。你的选择只影响本机计算，不会修改教务数据，也不会写回学校。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.selectableGroup()) {
                        PolicyOption(
                            label = "暂不确认（存在候选时不出数）",
                            selected = uiState.policy == OutsidePlanGpaPolicy.UNCONFIRMED,
                            tag = "academic_gpa_policy_unconfirmed",
                            onSelect = { viewModel.setPolicy(OutsidePlanGpaPolicy.UNCONFIRMED) },
                        )
                        PolicyOption(
                            label = "计入本地 GPA",
                            selected = uiState.policy == OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN,
                            tag = "academic_gpa_policy_include",
                            onSelect = { viewModel.setPolicy(OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN) },
                        )
                        PolicyOption(
                            label = "不计入本地 GPA",
                            selected = uiState.policy == OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN,
                            tag = "academic_gpa_policy_exclude",
                            onSelect = { viewModel.setPolicy(OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN) },
                        )
                    }
                }
            }
            if (uiState.candidates.isNotEmpty()) {
                item {
                    AppSectionCard(
                        title = "方案外课程（${uiState.candidates.size} 门）",
                        modifier = Modifier.testTag("academic_gpa_candidates"),
                    ) {
                        uiState.candidates.forEach { (courseName, semesterDisplay) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.Xxs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    courseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    semesterDisplay,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                AppSectionCard(title = "规划") {
                    AppNavigationRow(
                        title = "成绩模拟（What-if）",
                        description = "试算未来成绩的影响，不影响任何已存记录",
                        onClick = onOpenSandbox,
                        modifier = Modifier.testTag("academic_gpa_open_sandbox"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyOption(
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Xs)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
