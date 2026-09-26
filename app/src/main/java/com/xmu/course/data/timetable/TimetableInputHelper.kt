package com.xmu.course.data.timetable

import com.xmu.course.data.CourseRepository
import com.xmu.course.parser.WeekPatternParser

/** 课表输入相关的纯计算边界，不负责持久化或网络访问。 */
object TimetableInputHelper {
    fun autoColor(name: String): String = CourseRepository.autoColor(name)

    fun parseWeeks(text: String): Set<Int> = WeekPatternParser.parse(text)
}
