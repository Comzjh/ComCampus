package com.xmu.course.data.academicimport.xlsx

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.ExtractedAcademicDocument
import com.xmu.course.data.academicimport.ExtractedAcademicPage
import com.xmu.course.data.academicimport.ExtractedTextRun
import com.xmu.course.data.academicimport.OneShotAcademicImportFileSource
import com.xmu.course.data.academicimport.PdfTextExtractor
import com.xmu.course.data.academicimport.adapter.AcademicImportSandboxMapper
import com.xmu.course.data.academicimport.parser.AcademicReportParser
import com.xmu.course.data.academicimport.review.AcademicImportDecisionMapper
import com.xmu.course.di.AcademicImportDependencies
import com.xmu.course.di.AcademicImportReviewMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportXlsxTest {
    @Test
    fun writerAndReaderRoundTripChineseTextDecimalCreditAndMissingValues() {
        val rows = listOf(
            AcademicXlsxRow(
                courseName = "微积分II-2 & 进阶",
                creditText = "5.0",
                status = "COMPLETED",
                sourcePage = 12,
                confidence = "HIGH",
            ),
            AcademicXlsxRow(
                courseName = null,
                creditText = null,
                status = AcademicRecordKind.PLANNED.name,
                confidence = "LOW",
                reviewReason = "MISSING_NAME|MISSING_CREDIT",
            ),
        )
        val output = ByteArrayOutputStream()

        AcademicXlsxWriter().write(rows, output)
        val document = AcademicXlsxReader().read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(rows, document.rows)
        assertTrue(output.toByteArray().size > 100)
    }

    @Test
    fun temporaryStoreMakesExcelTheReadableDataSource() {
        val file = File.createTempFile("academic-import-", ".xlsx")
        try {
            val sourceRows = listOf(
                AcademicXlsxRow("普通课程", "3.0", status = "COMPLETED", confidence = "HIGH"),
            )

            val readBack = TemporaryAcademicXlsxStore(file).writeAndRead(sourceRows)

            assertTrue(file.exists())
            assertEquals(sourceRows, readBack.rows)
        } finally {
            assertTrue(file.delete())
        }
    }

    @Test
    fun pdfReviewIsConvertedToXlsxAndPreviewReadsBackThatXlsx() {
        val file = File.createTempFile("academic-import-pipeline-", ".xlsx")
        try {
            val dependencies = AcademicImportDependencies(
                pdfTextExtractor = object : PdfTextExtractor {
                    override fun extract(input: java.io.InputStream) = ExtractedAcademicDocument(
                        listOf(
                            ExtractedAcademicPage(
                                pageNumber = 3,
                                textRuns = listOf(
                                    ExtractedTextRun(
                                        text = "已修 课程：微积分II-2 学分：5.0",
                                        x = 0f,
                                        y = 0f,
                                        width = 200f,
                                        height = 10f,
                                    ),
                                ),
                            ),
                        ),
                    )
                },
                reportParser = AcademicReportParser(),
                reviewMapper = AcademicImportReviewMapper(),
                decisionMapper = AcademicImportDecisionMapper(),
                sandboxMapper = AcademicImportSandboxMapper(),
                temporaryXlsxStore = TemporaryAcademicXlsxStore(file),
            )

            val preview = kotlinx.coroutines.runBlocking {
                dependencies.previewFromXlsx(
                    OneShotAcademicImportFileSource(ByteArrayInputStream(byteArrayOf(1))),
                )
            }

            assertEquals("微积分II-2", preview.document.rows.single().courseName)
            assertEquals("5.0", preview.document.rows.single().creditText)
            assertEquals("COMPLETED", preview.document.rows.single().status)
        } finally {
            file.delete()
        }
    }
}
