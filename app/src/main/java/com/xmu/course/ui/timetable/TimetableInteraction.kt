package com.xmu.course.ui.timetable

import com.xmu.course.domain.Timetable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/** 课表网格中一次空白区域命中的位置。星期一=1，星期日=7。 */
data class TimetableGridCell(
    val dayOfWeek: Int,
    val startSection: Int,
)

/** 课表日期与教学周计算，保持与 Compose 和 Room 无关，便于单元测试。 */
object TimetableCalendar {

    /**
     * 根据“第 1 周周一”的开学日期计算目标周的日期。
     * 非法日期、周次或星期返回 null，不猜测日期。
     */
    fun dateFor(startDate: String?, week: Int, dayOfWeek: Int): LocalDate? {
        if (week < 1 || dayOfWeek !in 1..7) return null
        val monday = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return monday
            .plusWeeks((week - 1).toLong())
            .plusDays((dayOfWeek - 1).toLong())
    }

    fun isToday(
        startDate: String?,
        week: Int,
        dayOfWeek: Int,
        today: LocalDate = LocalDate.now(),
    ): Boolean = dateFor(startDate, week, dayOfWeek) == today

    /** 根据开学日期计算实际教学周；开学前统一回退到第 1 周。 */
    fun currentWeek(startDate: String?, today: LocalDate = LocalDate.now()): Int {
        val monday = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 1
        if (today.isBefore(monday)) return 1
        return (ChronoUnit.DAYS.between(monday, today) / 7 + 1).toInt()
    }

    /** The actual teaching week shared by timetable surfaces, bounded by the configured semester length. */
    fun currentWeek(timetable: Timetable, today: LocalDate = LocalDate.now()): Int =
        currentWeek(timetable.startDate, today).coerceIn(1, timetable.totalWeeks.coerceAtLeast(1))

    fun formatMonthDay(date: LocalDate?): String = date?.let { "${it.monthValue}/${it.dayOfMonth}" }.orEmpty()
}

/** 网格命中计算所需的几何信息。 */
data class TimetableGridMetrics(
    val dayColumns: List<Int>,
    val columnWidthPx: Float,
    val sectionHeightPx: Float,
    val sectionCount: Int,
    /** 当 position 来自视口而非滚动内容时，需加回的纵向滚动距离。 */
    val scrollOffsetPx: Float = 0f,
)

/**
 * 将网格点击坐标转换为星期与起始节次。
 * x/y 位于网格视口坐标；scrollOffsetPx 将 y 转回滚动内容坐标。
 */
fun hitTestTimetableCell(
    xPx: Float,
    yPx: Float,
    metrics: TimetableGridMetrics,
): TimetableGridCell? {
    if (metrics.dayColumns.isEmpty() ||
        metrics.columnWidthPx <= 0f ||
        metrics.sectionHeightPx <= 0f ||
        metrics.sectionCount <= 0 ||
        !xPx.isFinite() ||
        !yPx.isFinite() ||
        !metrics.scrollOffsetPx.isFinite() ||
        xPx < 0f ||
        yPx < 0f
    ) {
        return null
    }

    val columnIndex = floor(xPx / metrics.columnWidthPx).toInt()
    val contentY = yPx + metrics.scrollOffsetPx
    val section = floor(contentY / metrics.sectionHeightPx).toInt() + 1
    if (columnIndex !in metrics.dayColumns.indices || section !in 1..metrics.sectionCount) return null

    return TimetableGridCell(
        dayOfWeek = metrics.dayColumns[columnIndex],
        startSection = section,
    )
}

/** 把现有 lane 布局转换为“哪些节次已经被课程占用”的纯数据。 */
fun occupiedTimetableSections(
    layout: Map<Int, List<TimetableLayoutItem>>,
    dayColumns: List<Int>,
    sectionCount: Int,
): Array<BooleanArray> = Array(dayColumns.size) { columnIndex ->
    BooleanArray(sectionCount) { index ->
        val section = index + 1
        layout[dayColumns[columnIndex]].orEmpty().any { item ->
            section >= item.course.startSection &&
                section < item.course.startSection + item.course.duration
        }
    }
}
