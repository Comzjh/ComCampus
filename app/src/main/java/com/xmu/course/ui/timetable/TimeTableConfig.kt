package com.xmu.course.ui.timetable

/**
 * 作息时间配置（依据厦大实际作息：11 节制，"中午"为午休分隔行，不计节次）。
 * UI 展示通过 [xmuSections] 读取，不在布局/界面中硬编码。
 */
object TimeTableConfig {

    /** 每节上课时间段（"HH:mm-HH:mm"）。 */
    val xmuSections: List<String> = listOf(
        "08:00-08:45", // 第1节
        "08:55-09:40", // 第2节
        "10:10-10:55", // 第3节
        "11:05-11:50", // 第4节
        // 12:00-14:00 为午休，无节次
        "14:30-15:15", // 第5节
        "15:25-16:10", // 第6节
        "16:40-17:25", // 第7节
        "17:35-18:20", // 第8节
        "19:10-19:55", // 第9节（晚间）
        "20:05-20:50", // 第10节（晚间）
        "21:00-21:45", // 第11节（晚间）
    )

    /** 总节数。 */
    val sectionCount: Int get() = xmuSections.size

    /** 返回第 [section] 节时间；越界返回空串。 */
    fun timeOf(section: Int): String = xmuSections.getOrNull(section - 1) ?: ""

    /** 按开始节次 + 持续节数返回完整上课时间（例如 1-4 节 => 08:00-11:50）。 */
    fun timeRange(startSection: Int, duration: Int): String =
        timeRangeBetween(startSection, startSection + duration - 1)

    /** 按开始/结束节次返回完整上课时间；任一节越界时回退单节时间。 */
    private fun timeRangeBetween(startSection: Int, endSection: Int): String {
        if (endSection < startSection) return timeOf(startSection)
        val start = xmuSections.getOrNull(startSection - 1)?.substringBefore('-')
        val end = xmuSections.getOrNull(endSection - 1)?.substringAfter('-')
        return if (start != null && end != null) "$start-$end" else timeOf(startSection)
    }
}

/** 时间轴自适应排版：格子高度降低时同步缩小字号和行高。 */
data class TimeAxisTypography(
    val numberSizeSp: Int,
    val timeSizeSp: Int,
    val numberLineHeightSp: Int,
    val timeLineHeightSp: Int,
    val spacingDp: Int,
)

/** 按格子高度返回时间轴排版，避免 50dp 及以下时段文字被裁切。 */
fun timeAxisTypography(sectionHeightDp: Float): TimeAxisTypography {
    val scale = (sectionHeightDp / 62f).coerceIn(0.62f, 1.1f)
    val number = (10 * scale).toInt().coerceIn(7, 11)
    val time = (8 * scale).toInt().coerceIn(5, 9)
    return TimeAxisTypography(
        numberSizeSp = number,
        timeSizeSp = time,
        numberLineHeightSp = number + 1,
        timeLineHeightSp = time + 1,
        spacingDp = 0,
    )
}

/**
 * 课表纵向布局唯一配置：TimeColumn 与 Grid 必须共享同一实例/数值。
 * 这里用 Float 表示 dp 值，避免测试与 UI 依赖 Compose 布局环境。
 */
data class TimetableLayoutConfig(
    val sectionHeightDp: Float = 56f,
    val sectionCount: Int = TimeTableConfig.sectionCount,
) {
    init {
        require(sectionHeightDp > 0f) { "sectionHeightDp must be positive" }
        require(sectionCount >= 0) { "sectionCount must not be negative" }
    }

    /** 第 [section] 节顶部 y（第 1 节为 0）。 */
    fun sectionTopDp(section: Int): Float = sectionHeightDp * (section - 1)

    /** 第 [section] 节底部 y，同时也是下一节顶部横线 y。 */
    fun sectionBottomDp(section: Int): Float = sectionHeightDp * section

    /** 第 [section] 节中心 y；时间轴文字与网格同一来源计算。 */
    fun sectionCenterDp(section: Int): Float = sectionHeightDp * section - sectionHeightDp / 2f

    /** 网格横向分隔线 y；1 是第 1 节底边。 */
    fun gridLineYDp(lineIndex: Int): Float = sectionHeightDp * lineIndex

    val totalHeightDp: Float get() = sectionHeightDp * sectionCount
}

/** 紧凑/标准 Header 高度（不含背景），供布局与测试使用同一来源。 */
fun timetableHeaderHeightDp(compact: Boolean, statusBarPaddingDp: Float = 0f): Float =
    statusBarPaddingDp + if (compact) 46f else 82f
