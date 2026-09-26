package com.xmu.course.data.academicimport

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicImportFileSourceTest {
    @Test
    fun oneShotSourceExposesInputAndCallerCanCloseIt() = runBlocking {
        val source = OneShotAcademicImportFileSource(TrackingInputStream(byteArrayOf(1, 2, 3)))

        val input = source.open()
        assertArrayEquals(byteArrayOf(1, 2, 3), input.readBytes())
        input.close()

        assertTrue((input as TrackingInputStream).closed)
    }

    @Test
    fun oneShotSourceCannotBeReopenedOrPersistContent() = runBlocking {
        val source = OneShotAcademicImportFileSource(ByteArrayInputStream(byteArrayOf(7)))

        source.open()
        val error = runCatching { source.open() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("only be opened once") == true)
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
