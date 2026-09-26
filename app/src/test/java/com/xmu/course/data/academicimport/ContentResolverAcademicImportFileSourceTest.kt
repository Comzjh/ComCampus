package com.xmu.course.data.academicimport

import android.net.Uri
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentResolverAcademicImportFileSourceTest {
    @Test
    fun sourceOpensSelectedDocumentStreamOnce() = runBlocking {
        val source = ContentResolverAcademicImportFileSource(
            uri = Uri.parse("content://test/report.pdf"),
            openStream = { ByteArrayInputStream(byteArrayOf(4, 5)) },
        )

        assertArrayEquals(byteArrayOf(4, 5), source.open().readBytes())
        val error = runCatching { source.open() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun nullResolverStreamBecomesOpenFailure() = runBlocking {
        val source = ContentResolverAcademicImportFileSource(
            uri = Uri.parse("content://test/missing.pdf"),
            openStream = { null },
        )

        val error = runCatching { source.open() }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
    }
}
