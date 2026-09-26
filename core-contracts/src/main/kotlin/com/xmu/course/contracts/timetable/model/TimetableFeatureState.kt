package com.xmu.course.contracts.timetable.model

import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig

/** 课表功能展示所需的最小状态；不包含存储或 provider 实现类型。 */
data class TimetableFeatureState(
    val timetable: Timetable? = null,
    val courses: List<Course> = emptyList(),
    val config: TimetableConfig = TimetableConfig(timetableId = 0L),
    val skippedCourseIds: Set<Long> = emptySet(),
    val timetableLinks: Map<Long, TimetableMatchModel> = emptyMap(),
)
