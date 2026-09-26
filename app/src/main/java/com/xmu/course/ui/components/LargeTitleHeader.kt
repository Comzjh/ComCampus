package com.xmu.course.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xmu.course.ui.theme.AppSpacing

/**
 * 一级页面 Large Title 头部（Phase 9 · Apple large-title hierarchy）。
 *
 * 仅用于一级页面（课表/待办/成绩/我的/设置）；
 * 二级页面保持 Compact TopBar + Back，避免大标题滥用。
 */
@Composable
fun AppLargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleModifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(
        bottom = AppSpacing.Sm,
    )) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = if (trailingContent == null) Modifier else Modifier.weight(1f),
            )
            trailingContent?.invoke()
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.Xxs).then(subtitleModifier),
            )
        }
    }
}
