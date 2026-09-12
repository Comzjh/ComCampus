package com.xmu.course.domain

/** 背景类型：NONE=无背景；SOLID=纯色；BUILT_IN=内置厦大主题；CUSTOM=用户图片。 */
enum class BackgroundType { NONE, SOLID, BUILT_IN, CUSTOM }

/** 课程卡片文字水平对齐。 */
enum class TextHorizontalAlignment { START, CENTER, END }

/** 课程卡片文字垂直对齐。 */
enum class TextVerticalAlignment { TOP, CENTER, BOTTOM }

/** 多课表领域模型。 */
data class Timetable(
    val id: Long = 0L,
    val name: String,
    val semesterId: Long,
    val startDate: String? = null,
    val totalWeeks: Int = 25,
    val currentWeek: Int = 1,
    val createdTime: Long = 0L,
    val color: String? = null,
)

/** 每课表独立外观配置。 */
data class TimetableConfig(
    val timetableId: Long,
    val showSaturday: Boolean = false,
    val showSunday: Boolean = false,
    val showNonCurrentWeek: Boolean = false,
    val courseHeight: Int = 50,
    val cornerRadius: Int = 10,
    val textSize: Int = 12,
    val showTeacher: Boolean = true,
    val showLocation: Boolean = true,
    val showTime: Boolean = true,
    val showNote: Boolean = false,
    val courseAlpha: Float = 1f,
    val backgroundType: BackgroundType = BackgroundType.BUILT_IN,
    val backgroundValue: String? = "jiageng",
    val blurRadius: Int = 14,
    val overlayColor: Int = 0xFF000000.toInt(),
    val overlayAlpha: Float = 0.35f,
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
    val textHorizontalAlignment: TextHorizontalAlignment = TextHorizontalAlignment.CENTER,
    val textVerticalAlignment: TextVerticalAlignment = TextVerticalAlignment.CENTER,
    val showFullTimeAxis: Boolean = true,
    val headerCompactMode: Boolean = true,
)
