package com.xmu.course.data.academicimport.adapter

import com.xmu.course.contracts.academicimport.AcademicImportDecision
import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportSandboxMapperTest {
    private val mapper = AcademicImportSandboxMapper()

    @Test
    fun confirmedCourseBecomesEditableSandboxInput() {
        val result = mapper.map(
            AcademicImportDecision.Confirmed(
                courses = listOf(ConfirmedAcademicCourse("高等数学", "4.0")),
            ),
        )

        assertEquals(
            AcademicSandboxCourseInput(name = "高等数学", credits = "4.0", grade = ""),
            result.single(),
        )
    }

    @Test
    fun decimalCreditTextIsPreservedAndGradeIsNotInvented() {
        val result = mapper.map(
            AcademicImportDecision.Confirmed(
                courses = listOf(ConfirmedAcademicCourse("思想政治理论", "0.25")),
            ),
        )

        assertEquals("0.25", result.single().credits)
        assertTrue(result.single().grade.isEmpty())
    }

    @Test
    fun cancelledDecisionProducesNoSandboxInput() {
        assertTrue(mapper.map(AcademicImportDecision.Cancelled).isEmpty())
    }

    @Test
    fun mappingIsOneWayAndDoesNotRunGpaCalculation() {
        val result = mapper.map(
            AcademicImportDecision.Confirmed(
                courses = listOf(
                    ConfirmedAcademicCourse("课程一", "3"),
                    ConfirmedAcademicCourse("课程二", "3.0"),
                ),
            ),
        )

        assertEquals(listOf("3", "3.0"), result.map(AcademicSandboxCourseInput::credits))
        assertTrue(result.all { it.grade.isEmpty() })
    }
}
