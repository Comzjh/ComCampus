package com.xmu.course.data.timetable.adapter

import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.data.tronclass.matcher.TimetableMatchUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableMatchMapperTest {
    @Test
    fun `maps integration display model to feature contract`() {
        val source = TimetableMatchUiModel(
            name = "高等数学",
            instructor = "教师",
            semester = "2026 春",
        )

        assertEquals(
            TimetableMatchModel(
                name = "高等数学",
                instructor = "教师",
                semester = "2026 春",
            ),
            TimetableMatchMapper.toFeatureModel(source),
        )
    }
}
