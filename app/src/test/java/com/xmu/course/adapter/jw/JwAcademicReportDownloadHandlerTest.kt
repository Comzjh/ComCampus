package com.xmu.course.adapter.jw

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwAcademicReportDownloadHandlerTest {
    @Test
    fun forwardsLocalPdfContentUri() {
        val forwarded = mutableListOf<Uri>()
        val handler = JwAcademicReportDownloadHandler(forwarded::add)
        val uri = Uri.parse("content://downloads/report.pdf")

        assertTrue(handler.handle(JwAcademicPdfDownload(uri, "application/pdf")))
        assertEquals(listOf(uri), forwarded)
    }

    @Test
    fun acceptsFilePdfAndMimeParameters() {
        val forwarded = mutableListOf<Uri>()
        val handler = JwAcademicReportDownloadHandler(forwarded::add)
        val uri = Uri.parse("file:///temporary/report.pdf")

        assertTrue(
            handler.handle(
                JwAcademicPdfDownload(uri, " Application/PDF; charset=binary "),
            ),
        )
        assertEquals(listOf(uri), forwarded)
    }

    @Test
    fun rejectsNonPdfMimeAndDoesNotForward() {
        val forwarded = mutableListOf<Uri>()
        val handler = JwAcademicReportDownloadHandler(forwarded::add)

        assertFalse(
            handler.handle(
                JwAcademicPdfDownload(
                    Uri.parse("content://downloads/report.html"),
                    "text/html",
                ),
            ),
        )
        assertTrue(forwarded.isEmpty())
    }

    @Test
    fun rejectsRemoteOrBlobUriEvenWhenMimeIsPdf() {
        val forwarded = mutableListOf<Uri>()
        val handler = JwAcademicReportDownloadHandler(forwarded::add)

        listOf("https://jw.xmu.edu.cn/report.pdf", "blob:https://jw.xmu.edu.cn/report").forEach { rawUri ->
            assertFalse(
                handler.handle(
                    JwAcademicPdfDownload(Uri.parse(rawUri), "application/pdf"),
                ),
            )
        }
        assertTrue(forwarded.isEmpty())
    }

    @Test
    fun rejectsMissingMimeType() {
        val forwarded = mutableListOf<Uri>()
        val handler = JwAcademicReportDownloadHandler(forwarded::add)

        assertFalse(
            handler.handle(
                JwAcademicPdfDownload(
                    Uri.parse("content://downloads/report.pdf"),
                    null,
                ),
            ),
        )
        assertTrue(forwarded.isEmpty())
    }
}