package com.xmu.course.ui.datamanagement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppListDivider
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.tutorial.TutorialHost
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget

/**
 * 本地数据来源清单项（UI 聚合模型）。
 *
 * owner 只在组合根构造，经 sourceId 路由参数解析，不进入导航参数。
 */
data class DataManagementSource(
    val sourceId: String,
    val title: String,
    val description: String,
    val owner: PrivacyDataOwner,
)

/**
 * 数据与隐私：本机数据管理入口（Phase 11.12）。
 *
 * 聚合 Provider 数据（课表 / 畅课）与功能数据（官方成绩缓存 / 学业快照 / 旧导入成绩），
 * 进入各来源详情页复用 count → confirm → delete 流程。
 * 列表本身只读，不执行任何删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataManagementScreenContent(
    sources: List<DataManagementSource>,
    onOpenSource: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本机数据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TutorialToolbarAction() },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SectionGap),
        ) {
            AppGroupedSection(title = "按来源管理", modifier = Modifier.tutorialTarget(TutorialTargetKey.DATA_LIST)) {
                sources.forEachIndexed { index, source ->
                    if (index > 0) AppListDivider()
                    AppNavigationRow(
                        title = source.title,
                        description = source.description,
                        icon = Icons.Filled.Storage,
                        onClick = { onOpenSource(source.sourceId) },
                        modifier = Modifier.testTag("data_source_" + source.sourceId),
                    )
                }
            }
            Text(
                text = "删除只影响本机数据，不会退出登录，也不会自动重新同步。",
                modifier = Modifier.tutorialTarget(TutorialTargetKey.DATA_DELETE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DataManagementScreen(
    sources: List<DataManagementSource>,
    onOpenSource: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TutorialHost(tutorialId = "data_management", modifier = modifier.fillMaxSize()) {
        DataManagementScreenContent(
            sources = sources,
            onOpenSource = onOpenSource,
            onBack = onBack,
            modifier = Modifier,
        )
    }
}
