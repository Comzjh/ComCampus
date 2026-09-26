package com.xmu.course.data.academicimport.parser

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.fixture.AcademicImportFixtureLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicReportCoordinateParserTest {
    private val parser = AcademicReportParser()

    @Test
    fun detailBlockIsPreferredOverAggregatePathRow() {
        val result = parser.parse(AcademicImportFixtureLoader.coordinateDocument())

        val course = records(result).first { it.name == "高等数学" }
        assertEquals("4.0", course.creditText)
        assertEquals(AcademicRecordKind.COMPLETED, course.kind)
    }

    @Test
    fun coordinateParserKeepsSmallDecimalCredit() {
        val result = parser.parse(AcademicImportFixtureLoader.coordinateDocument())

        val parsed = records(result)
        val course = parsed.first { it.name == "社会实践" }
        assertEquals("0.5", course.creditText)
    }

    @Test
    fun detailBlockMayContinueOnNextPage() {
        val result = parser.parse(AcademicImportFixtureLoader.coordinateDocument())

        val course = records(result).first { it.name == "跨页课程" }
        assertEquals(2, course.pageNumber)
    }

    @Test
    fun missingDetailCreditRequiresReview() {
        val result = parser.parse(AcademicImportFixtureLoader.coordinateDocument())

        val review = result as AcademicImportParseResult.NeedsReview
        val course = review.records.first { it.name == "待确认课程" }
        assertEquals(null, course.creditText)
        assertTrue(AcademicReviewReason.CREDIT_MISSING in review.reasons)
    }

    private fun records(result: AcademicImportParseResult) = when (result) {
        is AcademicImportParseResult.Parsed -> result.records
        is AcademicImportParseResult.NeedsReview -> result.records
        is AcademicImportParseResult.Failed -> error("coordinate fixture should contain records")
    }

}
