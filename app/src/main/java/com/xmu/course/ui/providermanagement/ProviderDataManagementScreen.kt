package com.xmu.course.ui.providermanagement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.providermanagement.ProviderDataManagementViewModel.UiState
import com.xmu.course.ui.theme.AppSpacing

private const val DELETE_CONFIRM_TITLE = "删除此来源数据？"

/**
 * Provider 数据管理页（Phase 5.3.2-C3）。
 *
 * 流程：查看数量 → 点击删除 → AlertDialog 确认 → clearLocalData() → 刷新 count。
 * 取消不删除；确认只删除当前 Provider 数据。无网络、无同步、无认证清理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDataManagementScreen(
    viewModel: ProviderDataManagementViewModel,
    onBack: () -> Unit,
    title: String? = null,
) {
    val state by viewModel.state.collectAsState()
    val deleting by viewModel.deleting.collectAsState()
    val resultMessage by viewModel.resultMessage.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }
    val canDelete = when (val current = state) {
        is UiState.Loaded -> current.count > 0
        UiState.Unavailable -> viewModel.canClearLocalData
        UiState.Loading -> false
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: PROVIDER_DISPLAY_TITLES[viewModel.providerId] ?: viewModel.providerId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
        ) {
            AppSectionCard(title = "本机数据") {
                when (val current = state) {
                    UiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(vertical = AppSpacing.Sm)
                            .align(Alignment.CenterHorizontally),
                    )
                    is UiState.Loaded -> Text(
                        text = if (current.count > 0) "本机数据：${current.count} 条" else "本机暂无该来源数据",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("provider_local_count"),
                    )
                    UiState.Unavailable -> Text(
                        text = "本机数据暂时读不到",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "删除前会再确认一次，只影响这台手机上的该来源数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { showConfirmDialog = true },
                enabled = canDelete && !deleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (deleting) "删除中…" else "删除此来源数据")
            }
            resultMessage?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::consumeResultMessage) { Text("知道了") }
                }
            }
        }
    }

    if (showConfirmDialog) {
        val confirmationText = when (val current = state) {
            is UiState.Loaded ->
                "将删除当前来源的 ${current.count} 条本机数据，不可恢复。其他来源的数据不受影响。"
            UiState.Unavailable ->
                "当前来源的数据数量暂时无法读取。确认后会尝试删除该来源全部本机数据，不可恢复。其他来源的数据不受影响。"
            UiState.Loading -> "正在读取本机数据，请稍后重试。"
        }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(DELETE_CONFIRM_TITLE) },
            text = { Text(confirmationText) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        showConfirmDialog = false
                        viewModel.deleteLocalData()
                    },
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            },
        )
    }
}
