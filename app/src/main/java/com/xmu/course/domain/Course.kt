package com.xmu.course.domain

/** 课程来源。 */
enum class CourseSource { IMPORT, MANUAL }

/**
 * 课程领域模型（纯 Kotlin，无 Android 依赖）。
 *
 * 时间建模采用 [startSection] + [duration]（不使用 endSection）；
 * 上课周用 [weeks] 集合表达，天然支持全周/单双周/离散周混合。
 */
data class Course(
    val id: Long = 0L,
    val semesterId: Long = 0L,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val startSection: Int,
    val duration: Int,
    val weeks: Set<Int>,
    val source: CourseSource = CourseSource.MANUAL,
    val color: String = "",
    val note: String = "",
)

/**
 * 统一的周次判断入口：所有页面（课表/管理/统计）必须通过它判断某周是否上课，
 * 禁止在 Compose/Parser/LayoutEngine 各写一套单双周逻辑。
 */
fun Course.occursInWeek(week: Int): Boolean = week in weeks
