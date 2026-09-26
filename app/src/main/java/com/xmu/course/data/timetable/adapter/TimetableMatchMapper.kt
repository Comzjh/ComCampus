package com.xmu.course.data.timetable.adapter

import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.data.tronclass.matcher.TimetableMatchUiModel

/** 将现有 integration 展示模型转换为 Timetable Feature contract。 */
object TimetableMatchMapper {
    fun toFeatureModel(model: TimetableMatchUiModel): TimetableMatchModel =
        TimetableMatchModel(
            name = model.name,
            instructor = model.instructor,
            semester = model.semester,
        )
}
