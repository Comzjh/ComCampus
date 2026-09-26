package com.xmu.course.ui.providermanagement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import com.xmu.course.contracts.provider.AuthState
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderManagementEntry
import com.xmu.course.contracts.provider.SyncPolicy
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.AppSpacing

/** provider id 到 UI 文案的稳定映射；文案归 UI 层，Adapter/Descriptor 不返回展示字符串。 */
internal val PROVIDER_DISPLAY_TITLES = mapOf(
    "xmu.wisedu" to "金智教务",
    "xmu.tronclass" to "畅课",
    "xmu.jw" to "厦大教务",
)

private val PROVIDER_ICONS: Map<String, ImageVector> = mapOf(
    "xmu.wisedu" to Icons.Filled.School,
    "xmu.tronclass" to Icons.Filled.Layers,
    "xmu.jw" to Icons.Filled.AccountBalance,
)

private val CAPABILITY_DISPLAY_NAMES = mapOf(
    ProviderCapability.TIMETABLE to "课表",
    ProviderCapability.TODO to "待办",
    ProviderCapability.TRANSCRIPT to "成绩单",
    ProviderCapability.CAMPUS_SERVICE to "校园服务",
)

private val AUTH_STATE_DISPLAY_NAMES = mapOf(
    AuthState.AUTHENTICATED to "已配置",
    AuthState.AUTH_REQUIRED to "需要登录",
    AuthState.CHECKING to "检查中",
    AuthState.ERROR to "不可用",
)

private val AUTH_STATE_TONES = mapOf(
    AuthState.AUTHENTICATED to StatusTone.Success,
    AuthState.AUTH_REQUIRED to StatusTone.Warning,
    AuthState.CHECKING to StatusTone.Info,
    AuthState.ERROR to StatusTone.Error,
)

private val SYNC_POLICY_DISPLAY_NAMES = mapOf(
    SyncPolicy.MANUAL_ONLY to "手动",
    SyncPolicy.FOREGROUND_ALLOWED to "前台刷新",
    SyncPolicy.BACKGROUND_ALLOWED to "允许后台",
)

/**
 * 数据来源与管理：只读展示 Provider 清单（身份、能力、同步策略、运行状态）。
 *
 * 有 dataOwner 的 Provider 显示「管理数据」入口（仅导航，不执行任何删除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderManagementScreen(
    viewModel: ProviderManagementViewModel,
    onBack: () -> Unit,
    onManageData: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据来源") },
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
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.PagePadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = AppSpacing.PagePadding - AppSpacing.Md,
                bottom = AppSpacing.Xxl,
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppSpacing.ItemGap),
        ) {
            items(viewModel.entries, key = { it.descriptor.id }) { entry ->
                ProviderCard(entry = entry, onManageData = onManageData)
            }
        }
    }
}

@Composable
private fun ProviderCard(
    entry: ProviderManagementEntry,
    onManageData: (String) -> Unit,
) {
    val descriptor = entry.descriptor
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(AppSpacing.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PROVIDER_ICONS[descriptor.id]?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = AppSpacing.Md),
                    )
                }
                Text(
                    text = PROVIDER_DISPLAY_TITLES[descriptor.id] ?: descriptor.id,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                entry.statusSource?.currentState()?.let { state ->
                    AppStatusChip(
                        label = AUTH_STATE_DISPLAY_NAMES[state] ?: state.name,
                        tone = AUTH_STATE_TONES[state] ?: StatusTone.Neutral,
                    )
                }
            }
            Text(
                text = "能力: " + descriptor.capabilities.joinToString("、") {
                    CAPABILITY_DISPLAY_NAMES[it] ?: it.name
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.Md),
            )
            Text(
                text = "同步: " + (SYNC_POLICY_DISPLAY_NAMES[descriptor.syncPolicy] ?: descriptor.syncPolicy.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.Xs),
            )
            if (entry.dataOwner != null) {
                OutlinedButton(
                    onClick = { onManageData(descriptor.id) },
                    modifier = Modifier
                        .padding(top = AppSpacing.Lg)
                        .align(androidx.compose.ui.Alignment.Start),
                ) {
                    Text("管理数据")
                }
            }
        }
    }
}
