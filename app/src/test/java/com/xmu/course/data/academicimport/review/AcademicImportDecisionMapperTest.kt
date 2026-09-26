package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicImportDecision
import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence
import com.xmu.course.data.academicimport.parser.AcademicImportCandidateProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportDecisionMapperTest {
    private val mapper = AcademicImportDecisionMapper()

    @Test
    fun completedItemWithNameAndCreditBecomesConfirmedCourse() {
        val result = mapper.map(listOf(item("高等数学", "4", AcademicRecordKind.COMPLETED)))

        val decision = result.decision as AcademicImportDecision.Confirmed
        assertEquals("高等数学", decision.courses.single().name)
        assertEquals("4", decision.courses.single().creditText)
        assertTrue(result.rejectedItems.isEmpty())
    }

    @Test
    fun missingCreditIsRejectedAndNotConfirmed() {
        val result = mapper.map(listOf(item("待确认课程", null, AcademicRecordKind.COMPLETED)))

        assertTrue(result.decision === AcademicImportDecision.Cancelled)
        assertEquals(AcademicImportRejectionReason.CREDIT_MISSING, result.rejectedItems.single().reason)
    }

    @Test
    fun plannedCourseIsRejectedAndNotConfirmed() {
        val result = mapper.map(listOf(item("培养方案课程", "3.0", AcademicRecordKind.PLANNED)))

        assertTrue(result.decision === AcademicImportDecision.Cancelled)
        assertEquals(AcademicImportRejectionReason.NOT_COMPLETED, result.rejectedItems.single().reason)
    }

    @Test
    fun unknownKindIsRejectedWithoutGuessing() {
        val result = mapper.map(listOf(item("未知课程", "3.0", AcademicRecordKind.UNKNOWN)))

        assertTrue(result.decision === AcademicImportDecision.Cancelled)
        assertEquals(AcademicImportRejectionReason.NOT_COMPLETED, result.rejectedItems.single().reason)
    }

    @Test
    fun validAndInvalidItemsRemainSeparated() {
        val result = mapper.map(
            listOf(
                item("线性代数", "4.0", AcademicRecordKind.COMPLETED),
                item("待确认课程", null, AcademicRecordKind.UNKNOWN),
            ),
        )

        val decision = result.decision as AcademicImportDecision.Confirmed
        assertEquals(1, decision.courses.size)
        assertEquals(1, result.rejectedItems.size)
        assertEquals(AcademicImportRejectionReason.CREDIT_MISSING, result.rejectedItems.single().reason)
    }

    private fun item(
        name: String?,
        creditText: String?,
        kind: AcademicRecordKind,
    ) = AcademicImportReviewItem(
        name = name,
        creditText = creditText,
        kind = kind,
        confidence = AcademicParseConfidence.HIGH,
        pageNumber = 1,
        provenance = AcademicImportCandidateProvenance.COMPLETED_DETAIL,
    )
}
