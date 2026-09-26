package com.xmu.course.data.academicimport.parser

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.ExtractedAcademicDocument
import com.xmu.course.data.academicimport.ExtractedAcademicPage
import com.xmu.course.data.academicimport.ExtractedTextRun
import com.xmu.course.data.academicimport.fixture.AcademicImportFixtureLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicReportParserTest {
    private val parser = AcademicReportParser()

    @Test
    fun parserKeepsCreditTextAndDoesNotConvertItInTheParserLayer() {
        val result = parser.parse(documentOf(AcademicImportFixtureLoader.textSample("ordinary")))

        val parsed = result as AcademicImportParseResult.Parsed
        assertEquals("微积分II-2", parsed.records.single().name)
        assertEquals("3.0", parsed.records.single().creditText)
        assertEquals(AcademicRecordKind.COMPLETED, parsed.records.single().kind)
        assertEquals(AcademicParseConfidence.HIGH, parsed.records.single().confidence)
    }

    @Test
    fun parserRetainsDecimalCreditWithoutRounding() {
        val result = parser.parse(documentOf(AcademicImportFixtureLoader.textSample("decimal")))

        val parsed = result as AcademicImportParseResult.Parsed
        assertEquals("0.25", parsed.records.single().creditText)
    }

    @Test
    fun plannedCourseIsNotSilentlyTreatedAsCompleted() {
        val result = parser.parse(documentOf(AcademicImportFixtureLoader.textSample("planned")))

        val review = result as AcademicImportParseResult.NeedsReview
        assertEquals(AcademicRecordKind.PLANNED, review.records.single().kind)
        assertTrue(AcademicReviewReason.PLANNED_COURSE_PRESENT in review.reasons)
    }

    @Test
    fun missingOrUnrecognizedRowsRequireReviewInsteadOfGuessing() {
        val result = parser.parse(documentOf(AcademicImportFixtureLoader.textSample("missing_credit")))

        val review = result as AcademicImportParseResult.NeedsReview
        assertEquals(null, review.records.single().creditText)
        assertTrue(AcademicReviewReason.CREDIT_MISSING in review.reasons)
    }

    @Test
    fun unrecognizedRowsStillProduceNoCourseCandidate() {
        val result = parser.parse(documentOf(AcademicImportFixtureLoader.textSample("summary_only")))

        val review = result as AcademicImportParseResult.NeedsReview
        assertTrue(AcademicReviewReason.NO_RELIABLE_COURSE_ROWS in review.reasons)
    }

    @Test
    fun lineAssemblerKeepsPageNumbersForCrossPageRows() {
        val document = ExtractedAcademicDocument(
            pages = listOf(
                page(1, AcademicImportFixtureLoader.textSample("cross_page_course")),
                page(2, AcademicImportFixtureLoader.textSample("cross_page_credit")),
            ),
        )

        val lines = AcademicReportLineAssembler().assemble(document.pages[1])
        assertEquals(2, lines.single().pageNumber)
        assertEquals("学分：2.0", lines.single().text)
    }

    private fun documentOf(text: String) = ExtractedAcademicDocument(listOf(page(1, text)))

    private fun page(pageNumber: Int, text: String): ExtractedAcademicPage {
        return ExtractedAcademicPage(
            pageNumber = pageNumber,
            textRuns = listOf(
                ExtractedTextRun(
                    text = text,
                    x = 10f,
                    y = 20f,
                    width = text.length * 8f,
                    height = 12f,
                ),
            ),
        )
    }
}
