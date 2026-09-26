package com.xmu.course.contracts

import com.xmu.course.domain.Timetable

/** 课表管理列表对外暴露的摘要模型，不包含 Room 查询投影细节。 */
data class TimetableSummary(
    val timetable: Timetable,
    val courseCount: Int,
    val isCustom: Boolean,
)
