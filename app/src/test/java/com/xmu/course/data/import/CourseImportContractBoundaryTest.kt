package com.xmu.course.data.import

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseImportContractBoundaryTest {
    @Test
    fun `legacy contract exposes only prepare and commit capabilities`() {
        val source = Files.readAllLines(sourceFile("data", "import", "CourseImportContract.kt"))
        val suspendMethods = source.count { it.trimStart().startsWith("suspend fun") }

        assertEquals(2, suspendMethods)
        assertTrue(source.any { "suspend fun prepareImport" in it })
        assertTrue(source.any { "suspend fun commitImport" in it })
    }

    @Test
    fun `legacy contract does not expose runtime implementation details`() {
        val source = Files.readAllLines(sourceFile("data", "import", "CourseImportContract.kt"))
        val forbiddenDetails = listOf(
            "WebView",
            "Cookie",
            "Session",
            "AppDatabase",
            "Dao",
            "ViewModel",
            "NavController",
            "Context",
        )

        forbiddenDetails.forEach { detail ->
            assertFalse("Contract must not expose $detail", source.any { detail in it })
        }
    }

    private fun sourceFile(vararg parts: String): Path = listOf(
        Paths.get("app", "src", "main", "java", "com", "xmu", "course", *parts),
        Paths.get("src", "main", "java", "com", "xmu", "course", *parts),
    ).firstOrNull(Files::isRegularFile)
        ?: error("Cannot locate source file ${parts.joinToString("/")} from ${Paths.get("").toAbsolutePath()}")
}
