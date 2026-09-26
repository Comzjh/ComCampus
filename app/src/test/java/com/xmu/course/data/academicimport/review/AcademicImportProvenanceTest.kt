package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicImportDecision
import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.parser.AcademicImportCandidateProvenance
import com.xmu.course.data.academicimport.parser.AcademicImportParseResult
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence
import com.xmu.course.data.academicimport.parser.AcademicRecordCandidate
import com.xmu.course.data.academicimport.parser.AcademicReviewReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportProvenanceTest {
    @Test
    fun provenanceHasOnlyTheThreeParserFacts() {
        assertEquals(
            setOf(
                AcademicImportCandidateProvenance.COMPLETED_DETAIL,
                AcademicImportCandidateProvenance.PROGRAM_REQUIREMENT,
                AcademicImportCandidateProvenance.UNKNOWN_SECTION,
            ),
            AcademicImportCandidateProvenance.entries.toSet(),
        )
    }

    @Test
    fun provenanceDoesNotChangeCreditText() {
        val item = AcademicImportParseResult.Parsed(
            records = listOf(
                candidate(
                    creditText = "0.5",
                    provenance = AcademicImportCandidateProvenance.PROGRAM_REQUIREMENT,
                    kind = AcademicRecordKind.PLANNED,
                ),
            ),
        ).toReviewModel().items.single()

        assertEquals("0.5", item.creditText)
        assertEquals(AcademicImportCandidateProvenance.PROGRAM_REQUIREMENT, item.provenance)
    }

    @Test
    fun programRequirementDoesNotPassDecisionMapper() {
        val item = AcademicImportParseResult.Parsed(
            records = listOf(
                candidate(
                    provenance = AcademicImportCandidateProvenance.PROGRAM_REQUIREMENT,
                    kind = AcademicRecordKind.COMPLETED,
                ),
            ),
        ).toReviewModel().items

        val mapping = AcademicImportDecisionMapper().map(item)

        assertTrue(mapping.decision === AcademicImportDecision.Cancelled)
        assertEquals(
            AcademicImportRejectionReason.PROVENANCE_NOT_CONFIRMABLE,
            mapping.rejectedItems.single().reason,
        )
    }

    @Test
    fun completedDetailIsRequiredForConfirmation() {
        val item = AcademicImportParseResult.Parsed(
            records = listOf(
                candidate(
                    provenance = AcademicImportCandidateProvenance.COMPLETED_DETAIL,
                    kind = AcademicRecordKind.COMPLETED,
                ),
            ),
        ).toReviewModel().items

        val mapping = AcademicImportDecisionMapper().map(item)

        assertTrue(mapping.decision is AcademicImportDecision.Confirmed)
        assertTrue(mapping.rejectedItems.isEmpty())
    }

    @Test
    fun unknownSectionRemainsInReview() {
        val model = AcademicImportParseResult.NeedsReview(
            records = listOf(
                candidate(provenance = AcademicImportCandidateProvenance.UNKNOWN_SECTION),
            ),
            reasons = listOf(AcademicReviewReason.UNCERTAIN_SOURCE),
        ).toReviewModel()

        assertEquals(AcademicImportReviewStatus.NEEDS_REVIEW, model.status)
        assertEquals(AcademicImportReviewReason.UNCERTAIN_SOURCE, model.reasons.single())
        assertTrue(AcademicImportReviewReason.UNCERTAIN_SOURCE in model.items.single().reasons)
    }

    private fun candidate(
        creditText: String? = "3.0",
        provenance: AcademicImportCandidateProvenance,
        kind: AcademicRecordKind = AcademicRecordKind.COMPLETED,
    ) = AcademicRecordCandidate(
        name = "示例课程",
        creditText = creditText,
        kind = kind,
        confidence = AcademicParseConfidence.HIGH,
        pageNumber = 1,
        provenance = provenance,
    )
}
