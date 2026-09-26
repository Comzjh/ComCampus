package com.xmu.course.data.transcript

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptReaderAdapterTest {
    @Test
    fun defaultAdapterReportsUnavailableWithoutProvider() = runBlocking {
        assertNull(TranscriptReaderAdapter().getTranscript())
    }

    @Test
    fun adapterMapsOnlyTranscriptFieldsToGradesContract() = runBlocking {
        val legacy = TranscriptSnapshot(
            semester = SemesterRef("2026 春季学期", "20262"),
            courses = listOf(
                CourseTranscriptItem(
                    courseName = "高等数学",
                    teacher = "教师信息不属于 Grades Contract",
                    location = "地点信息不属于 Grades Contract",
                    score = "A-",
                    credits = 4.0,
                ),
            ),
        )

        val result = TranscriptReaderAdapter { legacy }.getTranscript()

        assertEquals("2026 春季学期", result?.semester?.name)
        assertEquals("20262", result?.semester?.code)
        assertEquals("高等数学", result?.courses?.single()?.courseName)
        assertEquals("A-", result?.courses?.single()?.score)
        assertEquals(4.0, result?.courses?.single()?.credits)
    }
}
