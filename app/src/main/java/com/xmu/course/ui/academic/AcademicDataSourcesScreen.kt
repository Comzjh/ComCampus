package com.xmu.course.ui.academic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.tutorial.TutorialAutoScroll
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget

/**
 * 数据与来源：刷新入口从学业首页降级到本页，同时汇总本机存量与原始页面分组。
 *
 * 本页只读展示本地 store 状态；刷新仍由用户点击触发，且不修改学校系统内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicDataSourcesScreenContent(
    viewModel: AcademicDataSourcesViewModel,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenGpaSettings: () -> Unit,
    onOpenAcademicReport: () -> Unit,
    onOpenCertificate: () -> Unit,
    onOpenCampusService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.testTag("academic_data_sources_screen"),
        topBar = {
            TopAppBar(
                title = { Text("数据与来源") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TutorialToolbarAction() },
            )
        },
    ) { innerPadding ->
        TutorialAutoScroll(scrollState, "academic_data_sources", innerPadding.calculateTopPadding())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
        ) {
            AppSectionCard(title = "学业数据", modifier = Modifier.testTag("datasources_academic_card")) {
                val statusText = when {
                    state.hasStorageFailure -> "部分本机数据读取失败"
                    state.hasAnyData -> "已保存到本机"
                    else -> "尚未刷新"
                }
                Text(statusText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("datasources_status"))
                state.gradeRefreshedAtText?.let { refreshedAt ->
                    Text(
                        "成绩上次刷新 $refreshedAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("datasources_last_refresh"),
                    )
                }
                if (state.hasStorageFailure) {
                    AppStatusChip("本机缓存异常 · 可在数据管理中清除后重试", tone = StatusTone.Warning)
                }
                // UX-06：手动刷新入口与「打开厦大教务」等课表导入 CTA 同档：实心按钮 + 明确 48dp 高度。
                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.Sm)
                        .heightIn(min = 48.dp)
                        .testTag("datasources_refresh_button")
                        .tutorialTarget(TutorialTargetKey.SOURCES_REFRESH),
                ) {
                    Text("刷新学业数据", modifier = Modifier.testTag("datasources_refresh_button_label"))
                }
                Text(
                    "会重新读取教务数据，不会修改学校系统中的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("datasources_refresh_disclaimer"),
                )
            }

            AppSectionCard(title = "本机数据", modifier = Modifier.testTag("datasources_local_card").tutorialTarget(TutorialTargetKey.SOURCES_LOCAL)) {
                DatasourcesStatRow(
                    label = "培养方案",
                    value = state.planName ?: if (state.planStorageFailed) "读取失败" else "尚未刷新",
                    tag = "datasources_plan_row",
                )
                if (state.planSynced) {
                    Text(
                        "学校页面统计时间 ${state.planSnapshotAt ?: "未知"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("datasources_plan_source_time"),
                    )
                    Text(
                        "培养方案本机获取时间 ${state.planFetchedAtText ?: "未记录"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("datasources_plan_fetch_time"),
                    )
                }
                DatasourcesStatRow(
                    label = "历史成绩",
                    value = if (state.gradeSynced) "${state.gradeCourseCount} 门课程" else if (state.gradeStorageFailed) "读取失败" else "尚未刷新",
                    tag = "datasources_grade_row",
                )
                DatasourcesStatRow(
                    label = "本学期在修",
                    value = "${state.enrolledCount} 门",
                    tag = "datasources_enrolled_row",
                )
                if (state.pendingManualCount > 0) {
                    DatasourcesStatRow(
                        label = "学分待确认",
                        value = "${state.pendingManualCount} 门",
                        tag = "datasources_pending_row",
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                AppNavigationRow(
                    title = "本机数据管理",
                    description = "查看或清除保存在这台手机上的数据",
                    onClick = onOpenDataManagement,
                    modifier = Modifier.testTag("datasources_open_data_management"),
                )
                Text(
                    "删除只删除本机缓存，不影响学校系统。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("datasources_delete_disclaimer"),
                )
            }

            AppGroupedSection(title = "GPA 设置") {
                AppNavigationRow(
                    title = "GPA 计算设置",
                    description = "方案外课程是否计入本地 GPA；只影响本地计算",
                    onClick = onOpenGpaSettings,
                    modifier = Modifier.testTag("datasources_open_gpa_settings"),
                )
            }

            AppGroupedSection(title = "学校原始页面", modifier = Modifier.testTag("datasources_original_pages_card").tutorialTarget(TutorialTargetKey.SOURCES_ORIGINAL)) {
                AppNavigationRow(
                    title = "学业完成查询",
                    description = "培养方案进度与学分统计页面",
                    onClick = onOpenAcademicReport,
                    modifier = Modifier.testTag("datasources_open_academic_report"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                AppNavigationRow(
                    title = "证明申请",
                    description = "在读证明等证明文件入口",
                    onClick = onOpenCertificate,
                    modifier = Modifier.testTag("datasources_open_certificate"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                AppNavigationRow(
                    title = "更多校园服务",
                    description = "汇总其他在线服务入口",
                    onClick = onOpenCampusService,
                    modifier = Modifier.testTag("datasources_open_campus_service"),
                )
            }
        }
    }
}

@Composable
private fun DatasourcesStatRow(label: String, value: String, tag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Xxs)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xxs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AcademicDataSourcesScreen(
    viewModel: AcademicDataSourcesViewModel,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenGpaSettings: () -> Unit,
    onOpenAcademicReport: () -> Unit,
    onOpenCertificate: () -> Unit,
    onOpenCampusService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TutorialHost(tutorialId = "academic_data_sources", modifier = modifier.fillMaxSize()) {
        AcademicDataSourcesScreenContent(
            viewModel = viewModel,
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenDataManagement = onOpenDataManagement,
            onOpenGpaSettings = onOpenGpaSettings,
            onOpenAcademicReport = onOpenAcademicReport,
            onOpenCertificate = onOpenCertificate,
            onOpenCampusService = onOpenCampusService,
            modifier = Modifier,
        )
    }
}
