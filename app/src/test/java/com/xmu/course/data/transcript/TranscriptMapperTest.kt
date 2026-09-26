package com.xmu.course.data.transcript

import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptMapperTest {
    @Test
    fun `maps semester and course fields without inventing grades`() {
        val semester = Semester(
            name = "2026-2027 秋季学期",
            code = "20261",
            startDate = "2026-09-14",
        )
        val course = Course(
            name = "高等数学",
            teacher = "张老师",
            location = "翔安校区",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = setOf(1, 3, 5),
        )

        val snapshot = TranscriptMapper.fromCourses(semester, listOf(course))

        assertEquals(SemesterRef("2026-2027 秋季学期", "20261"), snapshot.semester)
        assertEquals(1, snapshot.courses.size)
        assertEquals("高等数学", snapshot.courses.single().courseName)
        assertEquals("张老师", snapshot.courses.single().teacher)
        assertEquals("翔安校区", snapshot.courses.single().location)
        assertNull(snapshot.courses.single().score)
        assertNull(snapshot.courses.single().credits)
    }
}
