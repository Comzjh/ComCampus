package com.xmu.course.di

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.ExtractedAcademicDocument
import com.xmu.course.data.academicimport.PdfTextExtractor
import com.xmu.course.data.academicimport.adapter.AcademicImportSandboxMapper
import com.xmu.course.data.academicimport.parser.AcademicImportParseResult
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence
import com.xmu.course.data.academicimport.parser.AcademicRecordCandidate
import com.xmu.course.data.academicimport.parser.AcademicReportParser
import com.xmu.course.data.academicimport.review.AcademicImportDecisionMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AcademicImportDependenciesTest {
    @Test
    fun capabilityKeepsAcademicImportStagesInjectable() {
        val extractor = object : PdfTextExtractor {
            override fun extract(input: java.io.InputStream): ExtractedAcademicDocument =
                ExtractedAcademicDocument(emptyList())
        }
        val parser = AcademicReportParser()
        val reviewMapper = AcademicImportReviewMapper()
        val decisionMapper = AcademicImportDecisionMapper()
        val sandboxMapper = AcademicImportSandboxMapper()
        val dependencies = AcademicImportDependencies(
            pdfTextExtractor = extractor,
            reportParser = parser,
            reviewMapper = reviewMapper,
            decisionMapper = decisionMapper,
            sandboxMapper = sandboxMapper,
        )

        assertSame(extractor, dependencies.pdfTextExtractor)
        assertSame(parser, dependencies.reportParser)
        assertSame(reviewMapper, dependencies.reviewMapper)
        assertSame(decisionMapper, dependencies.decisionMapper)
        assertSame(sandboxMapper, dependencies.sandboxMapper)
    }

    @Test
    fun reviewMapperIsTheOnlyCompositionStepThatMapsParseResultToReviewModel() {
        val result = AcademicImportParseResult.Parsed(
            records = listOf(
                AcademicRecordCandidate(
                    name = "高等数学",
                    creditText = "4.0",
                    kind = AcademicRecordKind.COMPLETED,
                    confidence = AcademicParseConfidence.HIGH,
                    pageNumber = 1,
                ),
            ),
        )

        val review = AcademicImportReviewMapper().map(result)

        assertEquals(1, review.items.size)
        assertEquals("高等数学", review.items.single().name)
        assertEquals("4.0", review.items.single().creditText)
    }
}
