package com.xmu.course.data.transcript

import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import com.xmu.course.contracts.grades.TranscriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcademicRecordTranscriptMapperTest {
    @Test
    fun mapsOnlyFactsWithoutGuessing() {
        val record = AcademicRecord(
            name = "数学",
            creditsText = "3.0",
            source = AcademicRecordSource.JW_REPORT,
        )

        val course = record.toTranscriptCourse()

        assertEquals("数学", course.courseName)
        assertEquals("3.0", course.creditsText)
        assertEquals(TranscriptSource.ACADEMIC_IMPORT, course.source)
    }

    @Test
    fun missingGradeFieldsStayNullWithoutInference() {
        val record = AcademicRecord(
            name = "数学",
            creditsText = "3.0",
            source = AcademicRecordSource.MANUAL_IMPORT,
        )

        val course = record.toTranscriptCourse()

        assertNull(course.score)
        assertNull(course.gradeText)
        assertNull(course.term)
        assertNull(course.credits)
    }
}
