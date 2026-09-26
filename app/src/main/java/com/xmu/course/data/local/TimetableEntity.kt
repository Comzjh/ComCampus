package com.xmu.course.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import com.xmu.course.domain.TimetableConfig

/**
 * 课表（v0.4 多课表）。
 *
 * 每个课表关联一个学期（semesterId）；自定义课表由 Repository 自动创建隐藏学期承载课程。
 */
@Entity(
    tableName = "timetables",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId")],
)
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val semesterId: Long,
    val startDate: String? = null,
    val totalWeeks: Int = 25,
    val currentWeek: Int = 1,
    val createdTime: Long = 0L,
    val color: String? = null,
)

/**
 * 每课表独立的外观配置。
 */
@Entity(
    tableName = "timetable_configs",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("timetableId")],
)
data class TimetableConfigEntity(
    @PrimaryKey val timetableId: Long,
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
    val backgroundType: String = BackgroundType.SOLID.name,
    val backgroundValue: String? = "#FFFFFFFF",
    val blurRadius: Int = 14,
    val overlayColor: Int = 0xFF000000.toInt(),
    val overlayAlpha: Float = 0f,
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
    val textHorizontalAlignment: String = TextHorizontalAlignment.CENTER.name,
    val textVerticalAlignment: String = TextVerticalAlignment.CENTER.name,
    val showFullTimeAxis: Boolean = true,
    val headerCompactMode: Boolean = true,
)

/** 实体 → 领域模型互转。 */
fun TimetableEntity.toDomain() = Timetable(
    id = id,
    name = name,
    semesterId = semesterId,
    startDate = startDate,
    totalWeeks = totalWeeks,
    currentWeek = currentWeek,
    createdTime = createdTime,
    color = color,
)

fun TimetableConfigEntity.toDomain() = TimetableConfig(
    timetableId = timetableId,
    showSaturday = showSaturday,
    showSunday = showSunday,
    showNonCurrentWeek = showNonCurrentWeek,
    courseHeight = courseHeight,
    cornerRadius = cornerRadius,
    textSize = textSize,
    showTeacher = showTeacher,
    showLocation = showLocation,
    showTime = showTime,
    showNote = showNote,
    courseAlpha = courseAlpha,
    backgroundType = runCatching { BackgroundType.valueOf(backgroundType) }.getOrDefault(BackgroundType.NONE),
    backgroundValue = backgroundValue,
    blurRadius = blurRadius,
    overlayColor = overlayColor,
    overlayAlpha = overlayAlpha,
    cropScale = cropScale,
    cropOffsetX = cropOffsetX,
    cropOffsetY = cropOffsetY,
    textHorizontalAlignment = runCatching { TextHorizontalAlignment.valueOf(textHorizontalAlignment) }
        .getOrDefault(TextHorizontalAlignment.CENTER),
    textVerticalAlignment = runCatching { TextVerticalAlignment.valueOf(textVerticalAlignment) }
        .getOrDefault(TextVerticalAlignment.CENTER),
    showFullTimeAxis = showFullTimeAxis,
    headerCompactMode = headerCompactMode,
)

fun TimetableConfig.toEntity() = TimetableConfigEntity(
    timetableId = timetableId,
    showSaturday = showSaturday,
    showSunday = showSunday,
    showNonCurrentWeek = showNonCurrentWeek,
    courseHeight = courseHeight,
    cornerRadius = cornerRadius,
    textSize = textSize,
    showTeacher = showTeacher,
    showLocation = showLocation,
    showTime = showTime,
    showNote = showNote,
    courseAlpha = courseAlpha,
    backgroundType = backgroundType.name,
    backgroundValue = backgroundValue,
    blurRadius = blurRadius,
    overlayColor = overlayColor,
    overlayAlpha = overlayAlpha,
    cropScale = cropScale,
    cropOffsetX = cropOffsetX,
    cropOffsetY = cropOffsetY,
    textHorizontalAlignment = textHorizontalAlignment.name,
    textVerticalAlignment = textVerticalAlignment.name,
    showFullTimeAxis = showFullTimeAxis,
    headerCompactMode = headerCompactMode,
)
