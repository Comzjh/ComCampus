package com.xmu.course.data.timetable

import com.xmu.course.data.CourseRepository
import com.xmu.course.parser.WeekPatternParser
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableInputHelperTest {
    @Test
    fun autoColorPreservesExistingRepositoryBehavior() {
        listOf("高等数学", "英语", "", "同名课程").forEach { name ->
            assertEquals(CourseRepository.autoColor(name), TimetableInputHelper.autoColor(name))
        }
    }

    @Test
    fun parseWeeksPreservesExistingParserBehavior() {
        listOf("1-16周", "1-3单周,6,8-9", "").forEach { text ->
            assertEquals(WeekPatternParser.parse(text), TimetableInputHelper.parseWeeks(text))
        }
    }
}
