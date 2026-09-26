package com.xmu.course.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
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
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppListDivider
import com.xmu.course.ui.theme.AppSpacing

/**
 * 「我的」标签页：登录与数据来源 / 数据与隐私 / 外观与启动 / 关于。
 *
 * 只呈现导航，状态读取仍由各自页面负责。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenAuthCenter: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenProviderManagement: () -> Unit = {},
    onOpenDataManagement: () -> Unit = {},
    onOpenTronClass: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
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
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.SectionGap),
    ) {
        AppGroupedSection(title = "登录与数据来源") {
            AppNavigationRow(
                title = "认证与登录",
                description = "查看统一认证与各来源登录状态",
                icon = Icons.Filled.Lock,
                onClick = onOpenAuthCenter,
                modifier = Modifier.testTag("profile_auth_center"),
            )
            AppListDivider()
            AppNavigationRow(
                title = "畅课",
                description = "课程与作业待办同步",
                icon = Icons.Filled.School,
                onClick = onOpenTronClass,
                modifier = Modifier.testTag("profile_tronclass"),
            )
        }
        AppGroupedSection(title = "支持 ComCampus") {
            AppNavigationRow(
                title = "支持项目维护",
                description = "自愿支持，帮助持续维护这个开源项目",
                icon = Icons.Filled.Favorite,
                onClick = onOpenSupport,
                modifier = Modifier.testTag("profile_support"),
            )
        }
        AppGroupedSection(title = "数据与隐私") {
            AppNavigationRow(
                title = "数据来源",
                description = "查看数据来源状态",
                icon = Icons.Filled.Storage,
                onClick = onOpenProviderManagement,
                modifier = Modifier.testTag("profile_provider_management"),
            )
            AppListDivider()
            AppNavigationRow(
                title = "本机数据",
                description = "查看与管理各来源的数据",
                icon = Icons.Filled.Storage,
                onClick = onOpenDataManagement,
                modifier = Modifier.testTag("profile_data_management"),
            )
        }
        AppGroupedSection(title = "外观与启动") {
            AppNavigationRow(
                title = "默认打开页面",
                description = "选择打开应用时先看到首页还是课表",
                icon = Icons.Filled.Settings,
                onClick = onOpenSettings,
                modifier = Modifier.testTag("profile_default_startup"),
            )
        }
        AppGroupedSection(title = "关于") {
            AppNavigationRow(
                title = "使用教程",
                icon = Icons.Filled.MenuBook,
                onClick = onOpenGuide,
                modifier = Modifier.testTag("profile_guide"),
            )
        }
    }
    }
}
