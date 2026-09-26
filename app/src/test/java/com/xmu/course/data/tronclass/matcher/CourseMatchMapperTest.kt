package com.xmu.course.data.tronclass.matcher

import com.xmu.course.data.tronclass.model.TronCourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseMatchMapperTest {
    @Test
    fun `映射只保留课表 UI 所需的畅课字段`() {
        val result = MatchResult(
            localCourseId = 7L,
            tronCourse = TronCourseEntity(
                tronCourseId = 10384L,
                name = "高等数学",
                semester = "2026-2027秋季",
                instructor = "畅课教师",
                updatedTime = 123L,
            ),
            strategy = MatchStrategy.FuzzyName,
        )

        assertEquals(
            TimetableMatchUiModel(
                name = "高等数学",
                instructor = "畅课教师",
                semester = "2026-2027秋季",
            ),
            CourseMatchMapper.toTimetableUiModel(result),
        )
    }
}
