package com.xmu.course.data.academicimport

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfBoxTextExtractorTest {
    @Test
    fun extractorReadsAValidPdfAndPreservesPageBoundary() {
        val context = RuntimeEnvironment.getApplication()
        PDFBoxResourceLoader.init(context)
        val bytes = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.addPage(PDPage())
                document.save(output)
            }
            output.toByteArray()
        }

        val extracted = PdfBoxTextExtractor(
            context,
        ).extract(ByteArrayInputStream(bytes))

        assertEquals(2, extracted.pageCount)
        assertEquals(listOf(1, 2), extracted.pages.map { it.pageNumber })
        assertTrue(extracted.pages.all { it.textRuns.isEmpty() })
    }
}
