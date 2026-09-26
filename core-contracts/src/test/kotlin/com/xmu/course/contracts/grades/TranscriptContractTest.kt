package com.xmu.course.contracts.grades

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class TranscriptContractTest {
    @Test
    fun readerCanRepresentUnavailableData() = runBlocking {
        val reader = object : TranscriptReader {
            override suspend fun getTranscript(): TranscriptSnapshot? = null
        }

        assertNull(reader.getTranscript())
    }

    @Test
    fun snapshotKeepsGradesFeatureShapeIndependentFromProviders() {
        val snapshot = TranscriptSnapshot(
            semester = TranscriptSemester("2026 春季学期", "20262"),
            courses = listOf(
                TranscriptCourse("高等数学", score = "A-", credits = 4.0),
            ),
        )

        assertEquals("20262", snapshot.semester.code)
        assertEquals("A-", snapshot.courses.single().score)
        assertEquals(4.0, snapshot.courses.single().credits)
    }

    @Test
    fun legacyConstructorKeepsNewFieldsNull() {
        val course = TranscriptCourse("高等数学", score = "A-", credits = 4.0)
        val snapshot = TranscriptSnapshot(
            semester = TranscriptSemester("2026 春季学期", "20262"),
            courses = listOf(course),
        )

        assertNull(course.creditsText)
        assertNull(course.gradeText)
        assertNull(course.term)
        assertNull(course.source)
        assertNull(snapshot.source)
        assertNull(snapshot.term)
    }

    @Test
    fun newFieldsCanBeDeclaredExplicitly() {
        val course = TranscriptCourse(
            courseName = "高等数学",
            score = null,
            credits = null,
            creditsText = "4.0",
            gradeText = "A-",
            term = "2026 春季学期",
            source = TranscriptSource.ACADEMIC_IMPORT,
        )
        val snapshot = TranscriptSnapshot(
            semester = TranscriptSemester("2026 春季学期", "20262"),
            courses = listOf(course),
            source = TranscriptSource.ACADEMIC_IMPORT,
            term = "2026 春季学期",
        )

        assertEquals("4.0", course.creditsText)
        assertEquals("A-", course.gradeText)
        assertEquals("2026 春季学期", course.term)
        assertEquals(TranscriptSource.ACADEMIC_IMPORT, course.source)
        assertEquals(TranscriptSource.ACADEMIC_IMPORT, snapshot.source)
        assertEquals("2026 春季学期", snapshot.term)
    }

    @Test
    fun transcriptSourceEnumHasExactlyThreeValues() {
        assertEquals(
            listOf(
                TranscriptSource.ACADEMIC_IMPORT,
                TranscriptSource.OFFICIAL_TRANSCRIPT,
                TranscriptSource.MANUAL,
            ),
            TranscriptSource.entries.toList(),
        )
    }
}
