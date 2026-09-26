package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.parser.AcademicImportParseResult
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence
import com.xmu.course.data.academicimport.parser.AcademicParseFailureReason
import com.xmu.course.data.academicimport.parser.AcademicRecordCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcademicImportReviewMapperTest {
    @Test
    fun parsedCandidateMapsToReadyReviewItemWithoutCreditConversion() {
        val model = AcademicImportParseResult.Parsed(
            records = listOf(
                AcademicRecordCandidate(
                    name = "微积分II-2",
                    creditText = "3.0",
                    kind = AcademicRecordKind.COMPLETED,
                    confidence = AcademicParseConfidence.HIGH,
                    pageNumber = 2,
                ),
            ),
        ).toReviewModel()

        assertEquals(AcademicImportReviewStatus.READY, model.status)
        assertEquals("3.0", model.items.single().creditText)
        assertEquals(2, model.items.single().pageNumber)
    }

    @Test
    fun needsReviewKeepsMissingCreditAndReason() {
        val model = AcademicImportParseResult.NeedsReview(
            records = listOf(
                AcademicRecordCandidate(
                    name = "待确认课程",
                    creditText = null,
                    kind = AcademicRecordKind.UNKNOWN,
                    confidence = AcademicParseConfidence.LOW,
                    pageNumber = 4,
                ),
            ),
            reasons = listOf(com.xmu.course.data.academicimport.parser.AcademicReviewReason.CREDIT_MISSING),
        ).toReviewModel()

        assertEquals(AcademicImportReviewStatus.NEEDS_REVIEW, model.status)
        assertNull(model.items.single().creditText)
        assertEquals(AcademicImportReviewReason.MISSING_CREDIT, model.reasons.single())
        org.junit.Assert.assertTrue(AcademicImportReviewReason.MISSING_CREDIT in model.items.single().reasons)
    }

    @Test
    fun failedParseDoesNotInventReviewItems() {
        val model = AcademicImportParseResult.Failed(AcademicParseFailureReason.NO_TEXT).toReviewModel()

        assertEquals(AcademicImportReviewStatus.FAILED, model.status)
        assertEquals(0, model.items.size)
        assertEquals(AcademicParseFailureReason.NO_TEXT, model.failureReason)
    }
}
