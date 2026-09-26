package com.xmu.course.contracts.timetable.model

/** 课表功能展示关联课程所需的 provider-neutral 最小数据。 */
data class TimetableMatchModel(
    val name: String,
    val instructor: String,
    val semester: String,
)
