package com.xmu.course.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.theme.AppShapes

/**
 * Apple-style 分组列表基础件（Phase 9）。
 *
 * 设计原则：同一分组内的多个 Row 共享一张 surface，
 * 行间用 inset divider 而不是每行独立 Card，消除 card-per-row 噪声。
 */

/** 分组标题：位于分组 surface 之上，弱化字号。 */
@Composable
fun AppGroupedListHeader(
    title: String,
    modifier: Modifier = Modifier,
    topSpacing: Dp = AppSpacing.Xxl,
) {
    Text(
        title,
        modifier = modifier.padding(
            start = AppSpacing.Lg + AppSpacing.Xs,
            end = AppSpacing.Lg,
            top = topSpacing,
            bottom = AppSpacing.Xs,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 行内分隔线：左侧随内容缩进，右侧出血，接近 iOS inset grouped divider。 */
@Composable
fun AppListDivider(
    modifier: Modifier = Modifier,
    inset: Dp = AppSpacing.Lg,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

/** 分组 surface 内的标准内容列。 */
@Composable
fun AppListGroupContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * Inset grouped section used for settings and navigation lists: the label sits
 * outside one shared surface, while callers own row dividers and row content.
 */
@Composable
fun AppGroupedSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppGroupedListHeader(title = title, topSpacing = 0.dp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.Card,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            AppListGroupContent(
                modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xs),
                content = content,
            )
        }
    }
}
