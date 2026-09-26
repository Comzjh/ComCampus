package com.xmu.course.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.AppSpacing
import com.xmu.course.ui.theme.ErrorToneDark
import com.xmu.course.ui.theme.ErrorToneLight
import com.xmu.course.ui.theme.InfoToneDark
import com.xmu.course.ui.theme.InfoToneLight
import com.xmu.course.ui.theme.NeutralToneDark
import com.xmu.course.ui.theme.NeutralToneLight
import com.xmu.course.ui.theme.StatusToneColors
import com.xmu.course.ui.theme.SuccessToneDark
import com.xmu.course.ui.theme.SuccessToneLight
import com.xmu.course.ui.theme.WarningToneDark
import com.xmu.course.ui.theme.WarningToneLight

/** 状态徽标的语义色调。 */
enum class StatusTone { Success, Warning, Info, Error, Neutral }

/**
 * 状态徽标：小圆点 + 文案。色调映射只发生在 UI 层，
 * Provider / contract 层不感知颜色。
 */
@Composable
fun AppStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val toneColors: StatusToneColors = when (tone) {
        StatusTone.Success -> if (isDark) SuccessToneDark else SuccessToneLight
        StatusTone.Warning -> if (isDark) WarningToneDark else WarningToneLight
        StatusTone.Info -> if (isDark) InfoToneDark else InfoToneLight
        StatusTone.Error -> if (isDark) ErrorToneDark else ErrorToneLight
        StatusTone.Neutral -> if (isDark) NeutralToneDark else NeutralToneLight
    }
    Row(
        modifier = modifier
            .background(toneColors.background, AppShapes.Chip)
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(toneColors.foreground, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = toneColors.foreground,
        )
    }
}
