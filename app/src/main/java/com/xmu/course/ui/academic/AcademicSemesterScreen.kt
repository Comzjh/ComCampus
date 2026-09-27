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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.ui.academiccompletion.AcademicCompletionViewModel
import com.xmu.course.ui.academiccompletion.CreditInputDialog
import com.xmu.course.ui.academiccompletion.EnrolledCourseRow
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing

/**
 * 本学期：在修课程官方事实 + 本地学分确认。
 *
 * 展示纪律沿用快照模型：来源确认 > 本地覆盖，本地确认绝不改写官方数据。
 * 数据只来自本机 AcademicCompletionStore，页面自身零网络访问。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicSemesterScreen(
    viewModel: AcademicCompletionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(onBack = onBack)

    LaunchedEffect(uiState.notice) {
        val message = uiState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNotice()
    }

    Scaffold(
        modifier = modifier.testTag("academic_semester_screen"),
        topBar = {
            TopAppBar(
                title = { Text("本学期") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState.storeState) {
            AcademicCompletionStore.State.Empty -> AppEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag("academic_semester_empty"),
                title = "本学期数据尚未刷新",
                description = "回到「学业」页点击刷新，即可从教务获取本学期课程。",
                icon = Icons.Outlined.School,
            )
            is AcademicCompletionStore.State.StorageFailure -> AppEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag("academic_semester_storage_failure"),
                title = "本机快照读取失败",
                description = state.message,
                icon = Icons.Outlined.School,
            )
            is AcademicCompletionStore.State.Loaded -> {
                val snapshot = state.snapshot
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(AppSpacing.PagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.ItemGap),
                ) {
                    item {
                        AppSectionCard(
                            title = snapshot.semesterLabel,
                            modifier = Modifier.testTag("academic_semester_header"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("academic_semester_summary_metrics"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xl),
                            ) {
                                Column(
                                    modifier = Modifier.weight(0.8f),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Xxs),
                                ) {
                                    Text(
                                        "${snapshot.enrolledCourses.size} 门",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("academic_semester_metric_courses"),
                                    )
                                    Text(
                                        "在修课程",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Xxs),
                                ) {
                                    Text(
                                        "学校页面统计",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        snapshot.plan.sourceSnapshotAt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("academic_semester_source_snapshot_time"),
                                    )
                                }
                            }
                            Text(
                                "本机获取时间 ${snapshot.fetchedAtEpochMillis?.let(::formatAcademicRefreshTime) ?: "未记录"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (snapshot.pendingManualCourses.isNotEmpty()) {
                                AppStatusChip(
                                    label = "学分待确认 ${snapshot.pendingManualCourses.size} 门",
                                    tone = StatusTone.Info,
                                    modifier = Modifier
                                        .padding(top = AppSpacing.Xs)
                                        .testTag("academic_semester_pending_chip"),
                                )
                            }
                        }
                    }
                    items(snapshot.enrolledCourses.size) { index ->
                        val course = snapshot.enrolledCourses[index]
                        Column(modifier = Modifier.fillMaxWidth()) {
                            EnrolledCourseRow(
                                snapshot = snapshot,
                                course = course,
                                onFillCredits = { viewModel.openCreditDialog(course) },
                            )
                            if (index != snapshot.enrolledCourses.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.creditDialogCourse?.let { course ->
        CreditInputDialog(
            course = course,
            value = uiState.creditInput,
            errorMessage = uiState.creditErrorMessage,
            onValueChange = viewModel::onCreditInputChange,
            onSubmit = viewModel::submitCreditDialog,
            onDismiss = viewModel::dismissCreditDialog,
        )
    }
}
