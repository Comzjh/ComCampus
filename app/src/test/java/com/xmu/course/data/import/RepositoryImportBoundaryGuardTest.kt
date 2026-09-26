package com.xmu.course.data.import

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryImportBoundaryGuardTest {
    @Test
    fun `ui production code does not bypass coordinator for import`() {
        val uiRoot = sourceRoot("ui")
        val forbiddenPatterns = listOf(
            "CourseImportContract",
            "CourseRepository.prepareImport",
            "CourseRepository.commitImport",
            "courseRepository.prepareImport",
            "courseRepository.commitImport",
            "repository.prepareImport",
            "repository.commitImport",
        )
        val violations = kotlinFiles(uiRoot).flatMap { file ->
            val source = Files.readAllLines(file).joinToString("\n")
            forbiddenPatterns
                .filter { it in source }
                .map { pattern -> "$file: $pattern" }
        }

        assertTrue(
            "UI import flow must use ImportCoordinator rather than repository import methods: $violations",
            violations.isEmpty(),
        )
    }

    private fun sourceRoot(vararg parts: String): Path = listOf(
        Paths.get("app", "src", "main", "java", "com", "xmu", "course", *parts),
        Paths.get("src", "main", "java", "com", "xmu", "course", *parts),
    ).firstOrNull(Files::isDirectory)
        ?: error("Cannot locate source root ${parts.joinToString("/")} from ${Paths.get("").toAbsolutePath()}")

    private fun kotlinFiles(root: Path): List<Path> = Files.walk(root).use { stream ->
        stream
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .collect(Collectors.toList())
    }
}
