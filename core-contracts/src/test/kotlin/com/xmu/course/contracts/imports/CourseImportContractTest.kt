package com.xmu.course.contracts.imports

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseImportContractTest {
    @Test
    fun requestRepresentsProviderNeutralImportIntent() {
        assertEquals(
            CourseImportSource.JW,
            CourseImportRequest(source = CourseImportSource.JW).source,
        )
    }

    @Test
    fun resultRepresentsAllFeatureOutcomes() {
        val results: List<CourseImportResult> = listOf(
            CourseImportResult.Success(importedCount = 3),
            CourseImportResult.NoChanges,
            CourseImportResult.Unavailable,
            CourseImportResult.Failed(ImportFailureReason.UNKNOWN),
        )

        assertTrue(results[0] is CourseImportResult.Success)
        assertEquals(3, (results[0] as CourseImportResult.Success).importedCount)
        assertTrue(results[1] === CourseImportResult.NoChanges)
        assertTrue(results[2] === CourseImportResult.Unavailable)
        assertEquals(
            ImportFailureReason.UNKNOWN,
            (results[3] as CourseImportResult.Failed).reason,
        )
    }

    @Test
    fun coreImportContractsDoNotContainTransportOrStorageTypes() {
        val source = contractFiles()
            .flatMap(Files::readAllLines)
            .joinToString("\n")
        val forbidden = listOf(
            "rawHtml",
            "WebView",
            "Cookie",
            "Session",
            "DTO",
            "Entity",
            "android.",
            "com.xmu.course.data",
        )

        forbidden.forEach { marker ->
            assertFalse(
                marker in source,
                "Core import contract must not expose $marker",
            )
        }
    }

    private fun contractFiles(): List<Path> {
        val roots = listOf(
            Paths.get("src", "main", "kotlin", "com", "xmu", "course", "contracts", "imports"),
            Paths.get("core-contracts", "src", "main", "kotlin", "com", "xmu", "course", "contracts", "imports"),
        )
        val root = roots.firstOrNull(Files::isDirectory)
            ?: error("Cannot locate core import contract source root")
        return listOf(
            root.resolve("CourseImportRequest.kt"),
            root.resolve("CourseImportResult.kt"),
        )
    }
}
