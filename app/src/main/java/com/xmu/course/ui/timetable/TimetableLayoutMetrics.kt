package com.xmu.course.ui.timetable

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class TimetableLayoutMetrics(
    val timeAxisWidth: Dp,
    val cellHeight: Dp,
    val headerHeight: Dp,
)

/** 根据可用宽度缩放网格，避免小屏课程列被压到无法阅读。 */
internal fun calculateTimetableLayoutMetrics(
    maxWidth: Dp,
    configuredCellHeight: Dp,
): TimetableLayoutMetrics {
    val widthScale = (maxWidth.value / 360f).coerceIn(0.85f, 1.12f)
    return TimetableLayoutMetrics(
        timeAxisWidth = (60f * widthScale).coerceIn(50f, 68f).dp,
        cellHeight = (configuredCellHeight.value * widthScale).coerceIn(44f, 68f).dp,
        headerHeight = (46f * widthScale).coerceIn(42f, 52f).dp,
    )
}

// 仅提供轻微可读性遮罩，保留背景层，不覆盖课表主体。
internal const val TIMETABLE_GRID_OVERLAY_ALPHA = 0.08f
internal const val TIMETABLE_HEADER_OVERLAY_ALPHA = 0.18f

/**
 * 星期栏/日期栏可容纳的基准字号对（Phase 11：两栏字号独立后共同适配表头）。
 *
 * 头部内容高 ≈ 上下 padding 8 + 行距 2 + 星期行约 1.25×星期字号×fontScale + 日期胶囊（日期字号+7，dp 不随字体缩放）；
 * 两栏设置值之和超出表头容量时按同一比例回缩（下限 9sp），避免小屏或大字体模式下溢出裁切。
 *
 * @param fontScale 系统字体缩放（LocalDensity.fontScale），1.0 为默认。
 * @return 星期栏与日期栏实际渲染字号 (weekday, date)。
 */
internal fun fitDateHeaderFonts(
    headerHeightDp: Float,
    weekdayBaseSp: Int,
    dateBaseSp: Int,
    fontScale: Float = 1f,
): Pair<Int, Int> {
    val scale = fontScale.coerceAtLeast(1f)
    val needed = 1.25f * weekdayBaseSp * scale + dateBaseSp
    val available = headerHeightDp - 17f
    if (available <= 0f || needed <= available) {
        return weekdayBaseSp.coerceAtLeast(9) to dateBaseSp.coerceAtLeast(9)
    }
    val factor = available / needed
    val weekday = (weekdayBaseSp * factor).toInt().coerceIn(9, weekdayBaseSp)
    val date = (dateBaseSp * factor).toInt().coerceIn(9, dateBaseSp)
    return weekday to date
}
