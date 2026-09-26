package com.xmu.course.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前使用课表 ID（SharedPreferences 持久化 + StateFlow 响应式）。
 *
 * 不进 Room：只保存"用户当前打开哪个课表"这一 UI 状态。
 */
object TimetablePrefs {

    private const val PREFS = "timetable_prefs"
    private const val KEY_CURRENT_TIMETABLE = "current_timetable_id"
    private const val KEY_VIEW_WEEK_PREFIX = "view_week_"

    private const val KEY_AXIS_PERIOD_FONT = "axis_period_font_sp"
    private const val KEY_AXIS_TIME_FONT = "axis_time_font_sp"
    private const val KEY_AXIS_DATE_FONT = "axis_date_font_sp"
    private const val KEY_AXIS_WEEKDAY_FONT = "axis_weekday_font_sp"
    private const val KEY_AXIS_PERIOD_COLOR = "axis_period_color"
    private const val KEY_AXIS_TIME_COLOR = "axis_time_color"
    private const val KEY_AXIS_WEEKDAY_COLOR = "axis_weekday_color"
    private const val KEY_AXIS_DATE_COLOR = "axis_date_color"

    private val _currentTimetableId = MutableStateFlow<Long?>(null)
    val currentTimetableId: StateFlow<Long?> = _currentTimetableId.asStateFlow()

    /** 时间轴显示样式（全局偏好）；App 启动 load() 时恢复，设置页写入后即时生效。 */
    private val _axisStyle = MutableStateFlow(TimetableAxisStyle())
    val axisStyle: StateFlow<TimetableAxisStyle> = _axisStyle.asStateFlow()

    /** App 启动时调用一次，从 prefs 恢复。 */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.readSafely(-1L) { getLong(KEY_CURRENT_TIMETABLE, -1L) }
        _currentTimetableId.value = saved.takeIf { it > 0 }
        _axisStyle.value = readAxisStyle(context)
    }

    /** 读取某课表保存的查看周（未保存返回 null，调用方回退实际周）。 */
    fun getViewWeek(context: Context, timetableId: Long): Int? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .readSafely(-1) { getInt(KEY_VIEW_WEEK_PREFIX + timetableId, -1) }
        return saved.takeIf { it > 0 }
    }

    /** 保存某课表的查看周（每课表独立，切回时恢复）。 */
    fun setViewWeek(context: Context, timetableId: Long, week: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_VIEW_WEEK_PREFIX + timetableId, week).apply()
    }

    /** 切换当前课表；传 null 表示清除（例如删除了当前课表）。 */
    fun setCurrent(context: Context, id: Long?) {
        _currentTimetableId.value = id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (id == null) remove(KEY_CURRENT_TIMETABLE) else putLong(KEY_CURRENT_TIMETABLE, id)
        }.apply()
    }

    // ---- 时间轴文字样式（Phase 9.1 补丁：全局显示偏好，不进 Room） ----

    /** 读取并钳制时间轴样式；缺失或损坏的键自动回退默认值（向后兼容，不崩溃）。 */
    private fun readAxisStyle(context: Context): TimetableAxisStyle {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return TimetableAxisStyle(
            periodFontSp = prefs.readSafely(TimetableAxisStyle.DEFAULT_PERIOD_FONT_SP) {
                getInt(KEY_AXIS_PERIOD_FONT, TimetableAxisStyle.DEFAULT_PERIOD_FONT_SP)
            }
                .coerceIn(TimetableAxisStyle.periodFontRange),
            timeFontSp = prefs.readSafely(TimetableAxisStyle.DEFAULT_TIME_FONT_SP) {
                getInt(KEY_AXIS_TIME_FONT, TimetableAxisStyle.DEFAULT_TIME_FONT_SP)
            }
                .coerceIn(TimetableAxisStyle.timeFontRange),
            dateFontSp = prefs.readSafely(TimetableAxisStyle.DEFAULT_DATE_FONT_SP) {
                getInt(KEY_AXIS_DATE_FONT, TimetableAxisStyle.DEFAULT_DATE_FONT_SP)
            }
                .coerceIn(TimetableAxisStyle.dateFontRange),
            weekdayFontSp = prefs.readSafely(TimetableAxisStyle.DEFAULT_WEEKDAY_FONT_SP) {
                getInt(KEY_AXIS_WEEKDAY_FONT, TimetableAxisStyle.DEFAULT_WEEKDAY_FONT_SP)
            }
                .coerceIn(TimetableAxisStyle.weekdayFontRange),
            periodColor = parseAxisColor(prefs.readSafely<String?>(null) { getString(KEY_AXIS_PERIOD_COLOR, null) }),
            timeColor = parseAxisColor(prefs.readSafely<String?>(null) { getString(KEY_AXIS_TIME_COLOR, null) }),
            weekdayColor = parseAxisColor(prefs.readSafely<String?>(null) { getString(KEY_AXIS_WEEKDAY_COLOR, null) }),
            dateColor = parseAxisColor(prefs.readSafely<String?>(null) { getString(KEY_AXIS_DATE_COLOR, null) }),
        )
    }

    /** 未知/损坏的颜色键回退 AUTO。 */
    private fun parseAxisColor(name: String?): AxisTextColor =
        runCatching { AxisTextColor.valueOf(name.orEmpty()) }.getOrDefault(AxisTextColor.AUTO)

    private fun updateAxisStyle(
        context: Context,
        transform: (TimetableAxisStyle) -> TimetableAxisStyle,
    ) {
        val next = transform(_axisStyle.value)
        _axisStyle.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_AXIS_PERIOD_FONT, next.periodFontSp)
            putInt(KEY_AXIS_TIME_FONT, next.timeFontSp)
            putInt(KEY_AXIS_DATE_FONT, next.dateFontSp)
            putInt(KEY_AXIS_WEEKDAY_FONT, next.weekdayFontSp)
            putString(KEY_AXIS_PERIOD_COLOR, next.periodColor.name)
            putString(KEY_AXIS_TIME_COLOR, next.timeColor.name)
            putString(KEY_AXIS_WEEKDAY_COLOR, next.weekdayColor.name)
            putString(KEY_AXIS_DATE_COLOR, next.dateColor.name)
        }.apply()
    }

    /** 独立保存节次栏字号，不影响时间栏。 */
    fun setAxisPeriodFontSp(context: Context, sp: Int) =
        updateAxisStyle(context) {
            it.copy(periodFontSp = sp.coerceIn(TimetableAxisStyle.periodFontRange))
        }

    /** 独立保存时间栏字号，不影响节次栏。 */
    fun setAxisTimeFontSp(context: Context, sp: Int) =
        updateAxisStyle(context) {
            it.copy(timeFontSp = sp.coerceIn(TimetableAxisStyle.timeFontRange))
        }

    /** 独立保存日期栏字号（Phase 9.1 补丁扩展），不影响其他栏。 */
    fun setAxisDateFontSp(context: Context, sp: Int) =
        updateAxisStyle(context) {
            it.copy(dateFontSp = sp.coerceIn(TimetableAxisStyle.dateFontRange))
        }

    /** 独立保存星期栏字号（Phase 11：星期/日期完全分离），不影响其他栏。 */
    fun setAxisWeekdayFontSp(context: Context, sp: Int) =
        updateAxisStyle(context) {
            it.copy(weekdayFontSp = sp.coerceIn(TimetableAxisStyle.weekdayFontRange))
        }

    /** 独立保存节次栏颜色，不影响时间栏。 */
    fun setAxisPeriodColor(context: Context, color: AxisTextColor) =
        updateAxisStyle(context) { it.copy(periodColor = color) }

    /** 独立保存时间栏颜色，不影响节次栏。 */
    fun setAxisTimeColor(context: Context, color: AxisTextColor) =
        updateAxisStyle(context) { it.copy(timeColor = color) }

    /** 独立保存星期栏颜色（今天除外），不影响其他栏。 */
    fun setAxisWeekdayColor(context: Context, color: AxisTextColor) =
        updateAxisStyle(context) { it.copy(weekdayColor = color) }

    /** 独立保存日期栏颜色（今天胶囊除外），不影响其他栏。 */
    fun setAxisDateColor(context: Context, color: AxisTextColor) =
        updateAxisStyle(context) { it.copy(dateColor = color) }
}

/** 时间轴文字颜色选项。AUTO 跟随主题语义色，其余为双主题 dark-safe 预设。 */
enum class AxisTextColor { AUTO, INK, BRAND, SLATE, TEAL }

/**
 * 课表文字（节次栏/时间栏/星期栏/日期栏）显示样式。
 *
 * 四栏字号与颜色完全独立；星期栏/日期栏分离于 Phase 11。持久化为原始值（Int + 枚举名），
 * 不持久化 Compose Color，保证深浅色模式各自解析正确的颜色。
 */
data class TimetableAxisStyle(
    val periodFontSp: Int = DEFAULT_PERIOD_FONT_SP,
    val timeFontSp: Int = DEFAULT_TIME_FONT_SP,
    val dateFontSp: Int = DEFAULT_DATE_FONT_SP,
    val weekdayFontSp: Int = DEFAULT_WEEKDAY_FONT_SP,
    val periodColor: AxisTextColor = AxisTextColor.AUTO,
    val timeColor: AxisTextColor = AxisTextColor.AUTO,
    val weekdayColor: AxisTextColor = AxisTextColor.AUTO,
    val dateColor: AxisTextColor = AxisTextColor.AUTO,
) {
    companion object {
        /** Phase 16 验收：四栏字号默认统一 14sp（仅 fresh install；已保存用户值不回写）。 */
        const val DEFAULT_PERIOD_FONT_SP = 14
        const val DEFAULT_TIME_FONT_SP = 14
        const val DEFAULT_WEEKDAY_FONT_SP = 14
        const val DEFAULT_DATE_FONT_SP = 14

        /** Phase 16 验收：四栏共用同一量程，相同字号在滑块上的位置完全一致。 */
        val AXIS_FONT_RANGE = 9..18
        val periodFontRange = AXIS_FONT_RANGE
        val timeFontRange = AXIS_FONT_RANGE
        val dateFontRange = AXIS_FONT_RANGE
        val weekdayFontRange = AXIS_FONT_RANGE
    }
}
