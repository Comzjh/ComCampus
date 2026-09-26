package com.xmu.course.adapter.jw

import android.net.Uri
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.SyncPolicy
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XmuJwAdapterTest {
    private fun adapter(
        openStream: (Uri) -> InputStream? = { null },
        onOpen: () -> Unit = {},
        onCertificate: () -> Unit = {},
    ): XmuJwAdapter = XmuJwAdapter(
        onOpenAcademicReport = onOpen,
        onOpenCertificate = onCertificate,
        openDocumentStream = openStream,
    )

    @Test
    fun openAcademicReportDelegatesToInjectedLauncher() {
        var opened = 0
        val gateway = adapter(onOpen = { opened += 1 })

        gateway.openAcademicReport()
        gateway.openDocumentPage()

        assertEquals(2, opened)
    }

    @Test
    fun createOneShotPdfSourceRejectsNonPdfMime() {
        val source = adapter().createOneShotPdfSource(
            JwAcademicPdfDownload(Uri.parse("content://downloads/report.html"), "text/html"),
        )

        assertNull(source)
    }

    @Test
    fun createOneShotPdfSourceRejectsRemoteOrBlobUri() {
        listOf(
            "https://jw.xmu.edu.cn/report.pdf",
            "blob:https://jw.xmu.edu.cn/report",
        ).forEach { rawUri ->
            assertNull(
                adapter().createOneShotPdfSource(
                    JwAcademicPdfDownload(Uri.parse(rawUri), "application/pdf"),
                ),
            )
        }
    }

    @Test
    fun createOneShotPdfSourceRejectsMissingMime() {
        val source = adapter().createOneShotPdfSource(
            JwAcademicPdfDownload(Uri.parse("content://downloads/report.pdf"), null),
        )

        assertNull(source)
    }

    @Test
    fun createOneShotPdfSourceReturnsOneShotSourceForValidEvent() = runBlocking {
        var openCount = 0
        val jw = adapter(
            openStream = {
                openCount += 1
                ByteArrayInputStream(byteArrayOf(1, 2, 3))
            },
        )

        val source = jw.createOneShotPdfSource(
            JwAcademicPdfDownload(Uri.parse("content://downloads/report.pdf"), "application/pdf"),
        )
        assertTrue(source != null)
        assertArrayEquals(byteArrayOf(1, 2, 3), source!!.open().readBytes())

        val second = runCatching { source.open() }.exceptionOrNull()
        assertTrue(second is IllegalStateException)
        assertEquals(1, openCount)
    }

    @Test
    fun createOneShotPdfSourceFailsOpenWhenStreamUnavailable() = runBlocking {
        val source = adapter().createOneShotPdfSource(
            JwAcademicPdfDownload(Uri.parse("content://downloads/report.pdf"), "application/pdf"),
        )

        assertTrue(source != null)
        val error = runCatching { source!!.open() }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun descriptorDeclaresManualOnlyCampusService() {
        val descriptor = adapter().descriptor()

        assertEquals(JwCampusServices.PROVIDER_ID, descriptor.id)
        assertEquals(setOf(ProviderCapability.CAMPUS_SERVICE), descriptor.capabilities)
        assertEquals(SyncPolicy.MANUAL_ONLY, descriptor.syncPolicy)
    }

    @Test
    fun serviceIdentitiesAreStable() {
        assertEquals("xmu.jw", JwCampusServices.PROVIDER_ID)
        assertEquals("xmu.jw.academic_completion", JwCampusServices.ACADEMIC_COMPLETION)
        assertEquals("xmu.jw.certificate", JwCampusServices.CERTIFICATE)
        assertEquals("xmu.jw.transcript", JwCampusServices.TRANSCRIPT)
    }

    @Test
    fun servicesExposeImplementedAcademicCompletionAndCertificate() {
        val services = adapter().services()

        assertEquals(
            listOf(JwCampusServices.ACADEMIC_COMPLETION, JwCampusServices.CERTIFICATE),
            services.map { it.serviceId },
        )
        services.forEach { service ->
            assertEquals(ProviderCapability.CAMPUS_SERVICE, service.capability)
            assertTrue(service.displayKey.isNotBlank())
        }
    }

    @Test
    fun openServiceOpensImplementedCertificate() {
        var opened = 0

        adapter(onCertificate = { opened += 1 }).openService(JwCampusServices.CERTIFICATE)

        assertEquals(1, opened)
    }

    @Test
    fun openServiceOpensImplementedAcademicCompletion() {
        var opened = 0

        adapter(onOpen = { opened += 1 }).openService(JwCampusServices.ACADEMIC_COMPLETION)

        assertEquals(1, opened)
    }

    @Test
    fun openServiceRejectsUnimplementedOrUnknownServices() {
        listOf(
            JwCampusServices.TRANSCRIPT,
            "xmu.jw.unknown",
            "",
        ).forEach { serviceId ->
            val error = runCatching { adapter().openService(serviceId) }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
        }
    }
}