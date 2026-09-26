package com.xmu.course.contracts.academicimport

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AcademicImportDecisionContractTest {
    @Test
    fun confirmedDecisionPreservesUserAcceptedValues() {
        val course = ConfirmedAcademicCourse(name = "微积分II-2", creditText = "3.0")
        val decision = AcademicImportDecision.Confirmed(courses = listOf(course))

        assertEquals(course, decision.courses.single())
        assertEquals("3.0", decision.courses.single().creditText)
    }

    @Test
    fun cancelledDecisionIsExplicit() {
        assertTrue(AcademicImportDecision.Cancelled === AcademicImportDecision.Cancelled)
    }

    @Test
    fun blankConfirmedValuesCannotCrossTheConfirmationBoundary() {
        assertFailsWith<IllegalArgumentException> {
            ConfirmedAcademicCourse(name = "", creditText = "3.0")
        }
        assertFailsWith<IllegalArgumentException> {
            ConfirmedAcademicCourse(name = "课程", creditText = "")
        }
    }

    @Test
    fun contractDoesNotExposeAndroidFileOrProviderTypes() {
        val root = Paths.get("src", "main", "kotlin", "com", "xmu", "course", "contracts", "academicimport")
        val source = Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }

        listOf("android.", "Uri", "InputStream", "PdfBox", "Cookie", "Room", "JW", "Gpa").forEach { marker ->
            assertTrue(marker !in source, "Academic Import decision contract must not expose $marker")
        }
    }
}
