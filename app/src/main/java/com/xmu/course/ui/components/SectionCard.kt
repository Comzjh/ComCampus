package com.xmu.course.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.AppSpacing

/**
 * 分组卡片：列表页的分区容器，标题行 + 右侧可选操作 + 内容区。
 *
 * Phase 9：Apple grouped surface —— 白卡浮于浅灰页底，零阴影零描边，
 * 层级完全靠 surface 对比与间距表达。
 */
@Composable
fun AppSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(AppSpacing.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            Column(
                modifier = Modifier.padding(top = AppSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                content()
            }
        }
    }
}
