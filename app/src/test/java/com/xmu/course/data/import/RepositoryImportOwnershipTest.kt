package com.xmu.course.data.import

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryImportOwnershipTest {
    @Test
    fun `course repository still owns the import orchestration`() {
        val source = Files.readAllLines(sourceFile("data", "CourseRepository.kt"))

        assertTrue(source.any { "override suspend fun prepareImport" in it })
        assertTrue(source.any { "override suspend fun commitImport" in it })
        listOf(
            "parser.parse",
            "mergeImportedCourses",
            "semesterDao().getByCode",
            "withTransaction",
        ).forEach { marker ->
            assertTrue("CourseRepository must still contain $marker", source.any { marker in it })
        }
    }

    @Test
    fun `provider adapter does not absorb repository implementation details`() {
        val source = Files.readAllLines(
            sourceFile("data", "import", "provider", "CourseImportProviderAdapter.kt"),
        )

        assertFalse(source.any { "XmuKingosoftParser" in it })
        assertFalse(source.any { "withTransaction" in it })
        assertFalse(source.any { "AppDatabase" in it })
    }

    @Test
    fun `repository commit path owns the cross-table transaction`() {
        val source = Files.readAllLines(sourceFile("data", "CourseRepository.kt"))

        listOf(
            "db.withTransaction",
            "db.timetableDao().updateStartDateBySemesterId",
            "db.courseDao().deleteBySemester",
            "db.courseDao().insertAll",
        ).forEach { marker ->
            assertTrue("CourseRepository commit path must still contain $marker", source.any { marker in it })
        }
    }

    private fun sourceFile(vararg parts: String): Path = listOf(
        Paths.get("app", "src", "main", "java", "com", "xmu", "course", *parts),
        Paths.get("src", "main", "java", "com", "xmu", "course", *parts),
    ).firstOrNull(Files::isRegularFile)
        ?: error("Cannot locate source file ${parts.joinToString("/")} from ${Paths.get("").toAbsolutePath()}")
}
