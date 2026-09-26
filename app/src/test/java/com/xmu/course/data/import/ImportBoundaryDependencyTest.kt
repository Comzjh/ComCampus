package com.xmu.course.data.import

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBoundaryDependencyTest {
    @Test
    fun `legacy contract stays behind the provider adapter`() {
        val importRoot = listOf(
            Paths.get("app", "src", "main", "java", "com", "xmu", "course", "data", "import"),
            Paths.get("src", "main", "java", "com", "xmu", "course", "data", "import"),
        ).firstOrNull(Files::isDirectory)
            ?: error("Cannot locate import production source root from ${Paths.get("").toAbsolutePath()}")
        val productionFiles = kotlinFiles(importRoot)
            .filterNot { it.fileName.toString() == "CourseImportContract.kt" }
            .filterNot { it.fileName.toString() == "CourseImportProviderAdapter.kt" }
        val violations = productionFiles.flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.trimStart().startsWith("import ") &&
                    "CourseImportContract" in line
                ) {
                    "${file}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "Legacy CourseImportContract must stay behind CourseImportProviderAdapter: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFiles(root: Path): List<Path> = Files.walk(root).use { stream ->
        stream
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .collect(Collectors.toList())
    }
}
