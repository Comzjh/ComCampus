package com.xmu.course.ui.academic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing

/**
 * 历史成绩：按学期分组的官方成绩单缓存。
 *
 * 官方成绩/学分/绩点文本原样呈现；方案外标记只是培养方案的归属事实展示，
 * 绝不在此页做任何 GPA 或口径合并决策。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicHistoryScreen(
    viewModel: AcademicHistoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.testTag("academic_history_screen"),
        topBar = {
            TopAppBar(
                title = { Text("历史成绩") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.semesters.isEmpty()) {
            AppEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag("academic_history_empty"),
                title = if (uiState.storageFailed) "本机成绩缓存读取失败" else "历史成绩尚未刷新",
                description = if (uiState.storageFailed) {
                    "可在「数据管理」中清除损坏缓存，然后重新刷新。"
                } else {
                    "回到「学业」页点击刷新，即可补齐各学期成绩。"
                },
                icon = Icons.Outlined.History,
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    FilterChip(
                        selected = uiState.selectedSemesterCode == null,
                        onClick = { viewModel.selectSemester(null) },
                        label = { Text("全部学期") },
                        modifier = Modifier.testTag("academic_history_filter_all"),
                    )
                    uiState.semesters.forEach { semester ->
                        FilterChip(
                            selected = uiState.selectedSemesterCode == semester.row.semesterCode,
                            onClick = { viewModel.selectSemester(semester.row.semesterCode) },
                            label = { Text(semester.row.semesterDisplay) },
                            modifier = Modifier.testTag(
                                "academic_history_filter_${semester.row.semesterCode}",
                            ),
                        )
                    }
                }
            }
            uiState.visibleSemesters.forEach { semester ->
                item(key = "header_${semester.row.semesterCode}") {
                    AppSectionCard(
                        title = semester.row.semesterDisplay,
                        modifier = Modifier.testTag(
                            "academic_history_semester_${semester.row.semesterCode}",
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                        ) {
                            Text(
                                "${semester.row.courseCount} 门 · ${semester.row.creditsText} 学分",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (semester.row.hasOutsidePlanCourse) {
                                AppStatusChip("含方案外课程", tone = StatusTone.Warning)
                            }
                            semester.row.officialPointsAverageText?.let { average ->
                                Text(
                                    "按教务绩点均值 $average",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                semester.courses.forEachIndexed { index, course ->
                    item(key = "course_${semester.row.semesterCode}_$index") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseName, style = MaterialTheme.typography.bodyLarge)
                                    val meta = buildList {
                                        course.courseNatureDisplay?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        add("${course.creditsText} 学分")
                                    }.joinToString(" · ")
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (course.outsidePlan) {
                                    AppStatusChip("方案外", tone = StatusTone.Warning)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        course.gradeText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.testTag(
                                            "academic_history_grade_${semester.row.semesterCode}_$index",
                                        ),
                                    )
                                    course.pointGradeText
                                        ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                                        ?.let { points ->
                                            Text(
                                                "绩点 $points",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                }
                            }
                            if (index != semester.courses.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "教务页面 · 本机缓存。不同教务页面的统计口径可能不同：已修学分以培养方案为准，本页为成绩记录明细。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.Sm)
                        .testTag("academic_history_footnote"),
                )
            }
        }
    }
}
