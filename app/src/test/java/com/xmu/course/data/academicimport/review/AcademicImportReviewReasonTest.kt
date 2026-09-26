package com.xmu.course.data.academicimport.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportReviewReasonTest {
    @Test
    fun reviewReasonsAreStructuredAndCanCoexist() {
        val reasons = listOf(
            AcademicImportReviewReason.MISSING_CREDIT,
            AcademicImportReviewReason.AMBIGUOUS_DUPLICATE,
        )

        assertEquals(2, reasons.size)
        assertTrue(AcademicImportReviewReason.MISSING_CREDIT in reasons)
        assertTrue(AcademicImportReviewReason.AMBIGUOUS_DUPLICATE in reasons)
    }

    @Test
    fun reviewReasonModelDoesNotContainParserOrProviderTypes() {
        val modelPackage = requireNotNull(AcademicImportReviewReason::class.java.`package`).name

        assertEquals(
            "com.xmu.course.data.academicimport.review",
            modelPackage,
        )
    }
}
