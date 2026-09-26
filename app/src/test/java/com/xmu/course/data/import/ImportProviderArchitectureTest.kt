package com.xmu.course.data.import

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportProviderArchitectureTest {
    @Test
    fun `coordinator depends on provider and not the legacy contract`() {
        val source = Files.readAllLines(sourceFile("data", "import", "ImportCoordinator.kt"))

        assertTrue(source.any { "CourseImportProvider" in it })
        assertFalse(source.any { "CourseImportContract" in it })
    }

    @Test
    fun `provider package has only one legacy bridge`() {
        val providerRoot = sourceRoot("data", "import", "provider")
        val legacyReferences = kotlinFiles(providerRoot).flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.trimStart().startsWith("import ") &&
                    "CourseImportContract" in line
                ) {
                    "${file}:${index + 1}"
                } else {
                    null
                }
            }
        }

        val adapter = sourceFile(
            "data",
            "import",
            "provider",
            "CourseImportProviderAdapter.kt",
        )
        assertEquals(listOf("${adapter}:3"), legacyReferences)
    }

    @Test
    fun `course repository remains the legacy implementation owner`() {
        val source = Files.readAllLines(sourceFile("data", "CourseRepository.kt"))

        assertTrue(source.any { "CourseImportContract" in it })
    }

    @Test
    fun `app container wires the provider adapter at the composition root`() {
        val source = Files.readAllLines(sourceFile("di", "AppContainer.kt"))

        assertTrue(source.any { "CourseImportProviderAdapter" in it })
        assertTrue(source.any { "courseImportProvider = CourseImportProviderAdapter(courseRepository)" in it })
    }

    private fun sourceFile(vararg parts: String): Path = listOf(
        Paths.get("app", "src", "main", "java", "com", "xmu", "course", *parts),
        Paths.get("src", "main", "java", "com", "xmu", "course", *parts),
    ).firstOrNull(Files::isRegularFile)
        ?: error("Cannot locate source file ${parts.joinToString("/")} from ${Paths.get("").toAbsolutePath()}")

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
