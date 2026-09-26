package com.xmu.course.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.ComCampusDarkScheme
import com.xmu.course.ui.theme.ComCampusLightScheme

/**
 * 设计系统组件预览（Phase 8.10）。
 * 仅供 Android Studio / Compose 预览面板使用，不参与运行时逻辑。
 */

@Composable
private fun PreviewSurface(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) ComCampusDarkScheme else ComCampusLightScheme,
        shapes = androidx.compose.material3.Shapes(
            small = AppShapes.Button,
            medium = AppShapes.Card,
            large = AppShapes.Section,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.PagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
        ) {
            content()
        }
    }
}

@Preview(name = "SectionCard light", showBackground = true)
@Composable
private fun SectionCardPreview() {
    PreviewSurface(dark = false) {
        AppSectionCard(title = "校园服务") {
            AppNavigationRow(
                title = "学业完成查询",
                description = "前往厦大教务系统查看结果",
                icon = Icons.Filled.School,
                onClick = {},
            )
            AppNavigationRow(title = "证明申请", onClick = {})
        }
    }
}

@Preview(name = "SectionCard dark", showBackground = true)
@Composable
private fun SectionCardDarkPreview() {
    PreviewSurface(dark = true) {
        AppSectionCard(title = "学业功能") {
            AppNavigationRow(title = "成绩单", description = "查看已确认成绩", onClick = {})
        }
    }
}

@Preview(name = "Grouped section light", showBackground = true)
@Composable
private fun GroupedSectionPreview() {
    PreviewSurface(dark = false) {
        AppGroupedSection(title = "外观") {
            AppNavigationRow(
                title = "默认打开页面",
                description = "选择首页或课表",
                icon = Icons.Filled.Tune,
                onClick = {},
            )
            HorizontalDivider(modifier = Modifier.padding(start = AppSpacing.Lg))
            AppNavigationRow(
                title = "跟随系统色彩",
                description = "使用设备当前外观",
                icon = Icons.Filled.Tune,
                onClick = {},
            )
        }
    }
}

@Preview(name = "StatusChip tones", showBackground = true)
@Composable
private fun StatusChipPreview() {
    PreviewSurface(dark = false) {
        AppStatusChip(label = "已配置", tone = StatusTone.Success)
        AppStatusChip(label = "需要登录", tone = StatusTone.Warning)
        AppStatusChip(label = "检查中", tone = StatusTone.Info)
        AppStatusChip(label = "不可用", tone = StatusTone.Error)
        AppStatusChip(label = "未知", tone = StatusTone.Neutral)
    }
}

@Preview(name = "Empty state", showBackground = true)
@Composable
private fun EmptyStatePreview() {
    PreviewSurface(dark = false) {
        AppEmptyState(
            title = "暂无课程",
            description = "导入或手动添加课程后在这里查看",
            icon = Icons.Filled.School,
            actionLabel = "添加课程",
            onAction = {},
        )
    }
}
