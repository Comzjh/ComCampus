package com.xmu.course.contracts.grades

import kotlin.test.Test
import kotlin.test.assertEquals

class GpaSandboxCourseSourceContractTest {
    @Test
    fun `prefill result carries only feature data`() {
        val result = GpaSandboxPrefillResult(
            semesterName = "2026 秋",
            courses = listOf(GpaSandboxCourseDraft("高等数学", 4.0)),
            duplicateTimetableCourseCount = 1,
            unmatchedTimetableCourseNames = listOf("未匹配课程"),
            sourceAvailable = true,
        )

        assertEquals("2026 秋", result.semesterName)
        assertEquals(4.0, result.courses.single().credit)
        assertEquals(1, result.duplicateTimetableCourseCount)
    }
}
