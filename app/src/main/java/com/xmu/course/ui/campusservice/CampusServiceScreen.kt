package com.xmu.course.ui.campusservice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.xmu.course.contracts.campusservice.CampusServiceDescriptor
import com.xmu.course.ui.components.AppEmptyState
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.theme.AppSpacing

/** displayKey 到 UI 文案的稳定映射；文案归 UI 层，Adapter 不返回展示字符串。 */
private val SERVICE_DISPLAY_TITLES = mapOf(
    "campus_service.jw.academic_completion" to "学业完成查询",
    "campus_service.jw.certificate" to "证明申请",
)

private val SERVICE_DESCRIPTIONS = mapOf(
    "campus_service.jw.academic_completion" to "前往厦大教务系统查看结果",
    "campus_service.jw.certificate" to "在线申请成绩与在读证明",
)

private val SERVICE_ICONS: Map<String, ImageVector> = mapOf(
    "campus_service.jw.academic_completion" to Icons.Filled.School,
    "campus_service.jw.certificate" to Icons.Filled.Verified,
)

private const val MANUAL_ONLY_HINT = "仅在你点击时打开学校页面，不自动访问"

/**
 * 校园服务：CampusServiceProvider.services() 提供的官方只读页面入口。
 *
 * Feature 只依赖 CampusServiceProvider 契约；不感知 JW、URL、WebView、认证细节或 Adapter。
 * 培养方案进度等本机能力已并入「学业」正式功能区，此处不再重复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusServiceScreen(
    viewModel: CampusServiceViewModel,
    onBack: () -> Unit,
) {
    val unsupportedServiceId by viewModel.unsupportedServiceId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(onBack = onBack)

    LaunchedEffect(unsupportedServiceId) {
        val serviceId = unsupportedServiceId ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("暂不支持的服务：$serviceId")
        viewModel.consumeUnsupportedService()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园服务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.PagePadding),
        ) {
            if (viewModel.services.isEmpty()) {
                AppEmptyState(
                    title = "暂无可用服务",
                    description = "学校服务将由已接入的数据来源提供",
                    icon = Icons.Filled.Storefront,
                    modifier = Modifier.padding(top = AppSpacing.Md),
                )
            } else {
                AppGroupedSection(
                    title = "学校服务",
                    modifier = Modifier.padding(top = AppSpacing.Md),
                ) {
                    viewModel.services.forEachIndexed { index, service ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                        CampusServiceRow(
                            service = service,
                            onClick = { viewModel.openService(service.serviceId) },
                        )
                    }
                }
                Text(
                    text = MANUAL_ONLY_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        top = AppSpacing.Md,
                        start = AppSpacing.Xs,
                        end = AppSpacing.Xs,
                    ),
                )
            }

        }
    }
}

@Composable
private fun CampusServiceRow(
    service: CampusServiceDescriptor,
    onClick: () -> Unit,
) {
    AppNavigationRow(
        title = SERVICE_DISPLAY_TITLES[service.displayKey] ?: service.serviceId,
        description = SERVICE_DESCRIPTIONS[service.displayKey],
        icon = SERVICE_ICONS[service.displayKey] ?: Icons.Filled.Storefront,
        onClick = onClick,
    )
}
