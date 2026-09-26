package com.xmu.course.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.theme.AppSpacing

/**
 * 统一空态：图标 + 标题 + 可选说明 + 可选操作按钮。
 *
 * 替代各页面重复实现的「暂无数据」文案块（审计发现 15 种空态文案）。
 */
@Composable
fun AppEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    primaryAction: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) AppSpacing.Sm else AppSpacing.Xxl),
        horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (!compact) Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = if (compact) AppSpacing.Xs else AppSpacing.Lg),
        )
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (compact) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.padding(
                    top = AppSpacing.Xs,
                    start = if (compact) 0.dp else AppSpacing.Xxl,
                    end = if (compact) 0.dp else AppSpacing.Xxl,
                ),
            )
        }
        if (actionLabel != null && onAction != null) {
            if (primaryAction) {
                Button(onClick = onAction, modifier = Modifier.padding(top = AppSpacing.Md)) {
                    Text(actionLabel)
                }
            } else {
                FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = AppSpacing.Md)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
