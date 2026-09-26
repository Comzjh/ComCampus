package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportCandidateDeduplicatorTest {
    private val deduplicator = AcademicImportCandidateDeduplicator()

    @Test
    fun exactRepeatedCandidatesAreMarkedWithoutDroppingEitherRow() {
        val first = item(name = "高等数学", credit = "4")
        val second = item(name = " 高等数学 ", credit = "4")

        val result = deduplicator.deduplicate(listOf(first, second))

        assertTrue(result.uniqueItems.isEmpty())
        assertEquals(1, result.possibleDuplicates.size)
        assertEquals(
            listOf(first.name, second.name),
            result.possibleDuplicates.single().items.map(AcademicImportReviewItem::name),
        )
        assertEquals(
            listOf(AcademicImportReviewReason.AMBIGUOUS_DUPLICATE),
            result.possibleDuplicates.single().items.first().reasons,
        )
        assertTrue(result.possibleDuplicates.single().items.all {
            AcademicImportReviewReason.AMBIGUOUS_DUPLICATE in it.reasons
        })
    }

    @Test
    fun sameNameWithDifferentCreditRemainsSeparate() {
        val result = deduplicator.deduplicate(
            listOf(
                item(name = "数据结构", credit = "3"),
                item(name = "数据结构", credit = "4"),
            ),
        )

        assertEquals(2, result.uniqueItems.size)
        assertTrue(result.possibleDuplicates.isEmpty())
    }

    @Test
    fun sameNameAndCreditWithDifferentKindRemainsSeparate() {
        val result = deduplicator.deduplicate(
            listOf(
                item(name = "英语", credit = "2", kind = AcademicRecordKind.COMPLETED),
                item(name = "英语", credit = "2", kind = AcademicRecordKind.PLANNED),
            ),
        )

        assertEquals(2, result.uniqueItems.size)
        assertTrue(result.possibleDuplicates.isEmpty())
    }

    @Test
    fun missingNameOrCreditIsKeptForManualReviewAndIsNotAutoGrouped() {
        val missingName = item(name = null, credit = "2")
        val missingCredit = item(name = "待确认课程", credit = null)

        val result = deduplicator.deduplicate(listOf(missingName, missingCredit))

        assertEquals(listOf(missingName, missingCredit), result.uniqueItems)
        assertTrue(result.possibleDuplicates.isEmpty())
    }

    private fun item(
        name: String?,
        credit: String?,
        kind: AcademicRecordKind = AcademicRecordKind.COMPLETED,
    ) = AcademicImportReviewItem(
        name = name,
        creditText = credit,
        kind = kind,
        confidence = AcademicParseConfidence.HIGH,
        pageNumber = 1,
    )
}
