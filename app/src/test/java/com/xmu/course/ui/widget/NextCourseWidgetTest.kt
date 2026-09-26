package com.xmu.course.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class NextCourseWidgetTest {

    @Test
    fun ongoingCourseUsesOngoingTitle() {
        val course = widgetCourse(startMinute = 540, endMinute = 630)

        assertEquals("正在上课", nextCourseTitle(course, nowMinuteOfDay = 585))
        assertEquals("正在上课", WidgetRepository.countdownText(585, course))
    }

    @Test
    fun futureCourseKeepsNextTitle() {
        val course = widgetCourse(startMinute = 660, endMinute = 720)

        assertEquals("下一节课", nextCourseTitle(course, nowMinuteOfDay = 600))
    }

    @Test
    fun missingCourseKeepsEmptyStateTitle() {
        assertEquals("今日无课程", nextCourseTitle(null, nowMinuteOfDay = 600))
    }

    private fun widgetCourse(startMinute: Int, endMinute: Int) = WidgetCourse(
        id = 1L,
        name = "测试课程",
        location = "教室",
        startSection = 1,
        startTime = "09:00",
        startMinuteOfDay = startMinute,
        endMinuteOfDay = endMinute,
        isSkipped = false,
    )
}
