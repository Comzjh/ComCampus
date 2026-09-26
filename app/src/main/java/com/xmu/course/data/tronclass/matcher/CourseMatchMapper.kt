package com.xmu.course.data.tronclass.matcher

/**
 * 课表功能展示畅课匹配结果所需的最小数据模型。
 *
 * 匹配策略和 TronClass Room entity 只属于 integration/data 边界，不向 UI 暴露。
 */
data class TimetableMatchUiModel(
    val name: String,
    val instructor: String,
    val semester: String,
)

/** 将内部匹配结果映射为课表 UI 所需的数据。 */
object CourseMatchMapper {
    fun toTimetableUiModel(matchResult: MatchResult): TimetableMatchUiModel =
        TimetableMatchUiModel(
            name = matchResult.tronCourse.name,
            instructor = matchResult.tronCourse.instructor,
            semester = matchResult.tronCourse.semester,
        )
}
