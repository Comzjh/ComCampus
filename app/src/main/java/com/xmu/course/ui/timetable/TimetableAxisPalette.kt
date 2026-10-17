package com.xmu.course.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xmu.course.data.AxisTextColor
import com.xmu.course.ui.theme.AppSpacing

/**
 * 时间轴文字颜色预设（Phase 9.1）：每个选项提供浅/深色双值，
 * 避免把浅色板原样复制到深色模式导致不可读；AUTO 交回主题语义色。
 */
internal data class AxisColorOption(
    val key: AxisTextColor,
    val label: String,
    val light: Color?,
    val dark: Color?,
)

internal val axisColorOptions: List<AxisColorOption> = listOf(
    AxisColorOption(AxisTextColor.AUTO, "自动", null, null),
    AxisColorOption(AxisTextColor.INK, "墨黑", Color(0xFF171B21), Color(0xFFE1E5EC)),
    AxisColorOption(AxisTextColor.BRAND, "品牌蓝", Color(0xFF1A5CAB), Color(0xFF9ECAFF)),
    AxisColorOption(AxisTextColor.SLATE, "蓝灰", Color(0xFF516075), Color(0xFFB6C4DB)),
    AxisColorOption(AxisTextColor.TEAL, "青灰", Color(0xFF3E675F), Color(0xFFB5D3C9)),
)

/** 解析颜色键为当前主题下的实际颜色；AUTO 返回调用方给定的主题语义色。 */
@Composable
internal fun axisTextColor(key: AxisTextColor, auto: Color): Color {
    if (key == AxisTextColor.AUTO) return auto
    val option = axisColorOptions.first { it.key == key }
    val chosen = if (isSystemInDarkTheme()) option.dark else option.light
    return chosen ?: auto
}

/**
 * 颜色选择行：圆形色块 + 选中态（边框 + 对勾，不只靠颜色区分）。
 * AUTO 色块用中性底示意“跟随主题”。
 */
@Composable
internal fun AxisColorSwatchRow(
    selected: AxisTextColor,
    modifier: Modifier = Modifier,
    tagPrefix: String,
    onSelect: (AxisTextColor) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        axisColorOptions.forEach { option ->
            val isSelected = option.key == selected
            val isAuto = option.key == AxisTextColor.AUTO
            val swatchColor = if (isAuto) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                axisTextColor(option.key, MaterialTheme.colorScheme.onSurface)
            }
            val description = "使用${option.label}作为课表文字颜色"
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .testTag("${tagPrefix}_color_${option.key.name.lowercase()}")
                    .semantics { contentDescription = description }
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(option.key) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isAuto) MaterialTheme.colorScheme.onSurface else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
