package com.xmu.course.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xmu.course.data.auth.AuthStatus
import com.xmu.course.ui.components.AppSectionCard
import com.xmu.course.ui.components.AppStatusChip
import com.xmu.course.ui.components.StatusTone
import com.xmu.course.ui.theme.AppSpacing

/**
 * 统一认证中心：只呈现认证状态与入口，不触碰认证实现。
 *
 * 状态用 AppStatusChip 语义色呈现；内部枚举（AUTHENTICATED 等）不直接暴露给用户。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthCenterScreen(
    viewModel: AuthCenterViewModel,
    onOpenWisedu: () -> Unit,
    onLogoutWisedu: () -> Unit,
    wiseduLogoutSupported: Boolean = false,
    onOpenTronClass: () -> Unit,
    onLogoutTronClass: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("统一认证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新认证状态")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SectionGap),
        ) {
            Text(
                "教务与畅课使用相互独立的登录会话，退出其中一个不会影响另一个。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AuthServiceCard(
                title = "厦大教务",
                status = state.wisedu.status,
                purpose = "用于导入本地课表",
                description = if (wiseduLogoutSupported) {
                    "首次使用或状态未知时，请打开教务页面完成验证。"
                } else {
                    "首次使用或状态未知时，请打开教务页面完成验证；退出功能待真实认证链路审计后启用。"
                },
                openLabel = "打开教务",
                actionTagPrefix = "auth_wisedu",
                onOpen = onOpenWisedu,
                onLogout = onLogoutWisedu,
                logoutEnabled = wiseduLogoutSupported,
                modifier = Modifier.testTag("auth_card_wisedu"),
            )
            AuthServiceCard(
                title = "厦大畅课",
                status = state.tronClass.status,
                purpose = "用于课程、作业和学校待办同步",
                description = "登录畅课后即可同步课程与待办。",
                openLabel = "管理畅课",
                actionTagPrefix = "auth_tronclass",
                onOpen = onOpenTronClass,
                onLogout = onLogoutTronClass,
                modifier = Modifier.testTag("auth_card_tronclass"),
            )
            Text(
                "登录状态可能过期；需要刷新在线数据时会提示重新登录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.refreshing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_refreshing"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = AppSpacing.Sm))
                    Text("正在检查认证状态…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AuthServiceCard(
    title: String,
    status: AuthStatus,
    purpose: String,
    description: String,
    openLabel: String,
    actionTagPrefix: String,
    onOpen: () -> Unit,
    onLogout: () -> Unit,
    logoutEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val (label, tone) = statusChipPresentation(status)
    AppSectionCard(
        title = title,
        modifier = modifier,
        action = { AppStatusChip(label = label, tone = tone) },
    ) {
        Text(purpose, style = MaterialTheme.typography.bodyLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        statusGuidance(status)?.let { guidance ->
            Text(
                guidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.Sm)
                .testTag("auth_actions"),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("${actionTagPrefix}_open_button"),
            ) {
                Icon(Icons.Filled.Login, contentDescription = null)
                Text(openLabel, modifier = Modifier.padding(start = AppSpacing.Sm))
            }
            OutlinedButton(
                onClick = onLogout,
                enabled = logoutEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("${actionTagPrefix}_logout_button"),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Text(
                    if (logoutEnabled) "退出登录" else "退出登录（待验证）",
                    modifier = Modifier.padding(start = AppSpacing.Sm),
                )
            }
        }
    }
}

/** 内部枚举 → 用户可读徽标；映射只发生在 UI 层。 */
private fun statusChipPresentation(status: AuthStatus): Pair<String, StatusTone> = when (status) {
    AuthStatus.CHECKING -> "检查中" to StatusTone.Info
    AuthStatus.AUTHENTICATED -> "已认证" to StatusTone.Success
    AuthStatus.AUTH_REQUIRED -> "需要登录" to StatusTone.Warning
    AuthStatus.EXPIRED -> "已过期" to StatusTone.Warning
    AuthStatus.UNKNOWN -> "状态未知" to StatusTone.Neutral
}

private fun statusGuidance(status: AuthStatus): String? = when (status) {
    AuthStatus.EXPIRED -> "登录已过期，重新打开页面验证即可恢复。"
    AuthStatus.UNKNOWN -> "状态未知，请打开页面完成一次验证。"
    else -> null
}
