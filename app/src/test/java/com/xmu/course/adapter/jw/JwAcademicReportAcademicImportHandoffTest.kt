package com.xmu.course.adapter.jw

import android.net.Uri
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.xmu.course.data.academicimport.ContentResolverAcademicImportFileSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwAcademicReportAcademicImportHandoffTest {
    @Test
    fun acceptedPdfUriCanBeForwardedToExistingAcademicImportSource() = runBlocking {
        val uri = Uri.parse("content://downloads/academic-report.pdf")
        var forwarded: Uri? = null
        val handler = JwAcademicReportDownloadHandler { forwarded = it }

        assertTrue(handler.handle(JwAcademicPdfDownload(uri, "application/pdf")))
        assertTrue(forwarded == uri)

        val source = ContentResolverAcademicImportFileSource(
            uri = forwarded!!,
            openStream = { ByteArrayInputStream(byteArrayOf(7, 8, 9)) },
        )
        assertArrayEquals(byteArrayOf(7, 8, 9), source.open().readBytes())
    }
}