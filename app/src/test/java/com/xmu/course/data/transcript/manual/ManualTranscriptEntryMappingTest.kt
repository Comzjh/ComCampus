package com.xmu.course.data.transcript.manual

import com.xmu.course.contracts.grades.TranscriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 映射只搬运原文：缺失就是 null，不猜测学分、成绩或学期。 */
class ManualTranscriptEntryMappingTest {

    @Test
    fun `maps raw fields and preserves manual source`() {
        val course = ManualTranscriptEntry("数学", "3.0", "85", "2025春").toTranscriptCourse()
        assertEquals("数学", course.courseName)
        assertEquals("85", course.score)
        assertEquals(3.0, course.credits!!, 0.0001)
        assertEquals("3.0", course.creditsText)
        assertNull(course.gradeText)
        assertEquals("2025春", course.term)
        assertEquals(TranscriptSource.MANUAL, course.source)
    }

    @Test
    fun `unparsable credits keeps null credits but never loses raw text`() {
        val course = ManualTranscriptEntry("体育", "3学分", "优秀", "2025春").toTranscriptCourse()
        assertNull(course.credits)
        assertEquals("3学分", course.creditsText)
        assertEquals("优秀", course.score)
        assertEquals(TranscriptSource.MANUAL, course.source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank fields are rejected instead of guessed`() {
        ManualTranscriptEntry("数学", " ", "85", "2025春")
    }
}
