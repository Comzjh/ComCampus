package com.xmu.course.contracts.academicimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AcademicImportContractTest {
    @Test
    fun importedCourseExposesOnlyFeatureFields() {
        val course = AcademicImportedCourse(
            courseName = "微积分II-2",
            credit = 3.0,
            semester = "2025-2026-1",
            category = "专业基础课",
        )

        assertEquals("微积分II-2", course.courseName)
        assertEquals(3.0, course.credit)
        assertEquals("2025-2026-1", course.semester)
        assertEquals("专业基础课", course.category)
        assertTrue(course::class.java.declaredFields.map { it.name }.toSet().containsAll(
            setOf("courseName", "credit", "semester", "category"),
        ))
    }

    @Test
    fun invalidImportedCourseCannotEnterReadyResult() {
        assertFailsWith<IllegalArgumentException> {
            AcademicImportedCourse(courseName = "", credit = 3.0)
        }
        assertFailsWith<IllegalArgumentException> {
            AcademicImportedCourse(courseName = "课程", credit = 0.0)
        }
    }

    @Test
    fun resultCanSeparateReadyAndNeedsReview() {
        val ready = AcademicImportResult.Ready(
            courses = listOf(AcademicImportedCourse("线性代数", 4.0)),
        )
        val review = AcademicImportResult.NeedsReview(
            candidates = listOf(
                AcademicImportCandidate(
                    courseName = "待确认课程",
                    credit = null,
                    recordKind = AcademicRecordKind.UNKNOWN,
                ),
            ),
            issues = listOf(AcademicImportIssue(AcademicImportIssueReason.CREDIT_MISSING, 0)),
        )

        assertEquals(1, ready.courses.size)
        assertEquals(null, review.candidates.single().credit)
        assertEquals(AcademicImportIssueReason.CREDIT_MISSING, review.issues.single().reason)
    }

    @Test
    fun coreContractDoesNotExposeAndroidFileOrProviderTypes() {
        val sourceRoot = java.nio.file.Paths.get(
            "src", "main", "kotlin", "com", "xmu", "course", "contracts", "academicimport",
        )
        val source = java.nio.file.Files.walk(sourceRoot).use { paths ->
            paths.filter { java.nio.file.Files.isRegularFile(it) }
                .map { java.nio.file.Files.readString(it) }
                .toList()
                .joinToString("\n")
        }

        listOf("android.", "Uri", "InputStream", "PdfBox", "Cookie", "Room", "JW").forEach { marker ->
            assertTrue(marker !in source, "Academic Import core contract must not expose $marker")
        }
    }
}
