package com.xmu.course.data.import.provider

import com.xmu.course.data.import.CourseImportContract
import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult
import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CourseImportProviderAdapterTest {
    private val semester = Semester(name = "2026 秋季", code = "20261")
    private val courses = listOf(
        Course(
            name = "高等数学",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = setOf(1, 2),
        ),
    )

    @Test
    fun `preview forwards raw HTML to legacy import contract`() = runTest {
        val legacy = FakeLegacyImport()
        val expected = ImportPreparation.Ready(semester, courses)
        legacy.preparation = expected

        val actual = CourseImportProviderAdapter(legacy)
            .preview(CourseImportInput(rawHtml = "<table>courses</table>"))

        assertEquals(expected, actual)
        assertEquals("<table>courses</table>", legacy.lastHtml)
    }

    @Test
    fun `commit forwards Ready data and overwrite`() = runTest {
        val legacy = FakeLegacyImport(commitResult = ImportResult.Success(42L, 1))
        val preparation = ImportPreparation.Ready(semester, courses)

        val actual = CourseImportProviderAdapter(legacy).commit(preparation, overwrite = true)

        assertEquals(ImportResult.Success(42L, 1), actual)
        assertEquals(semester, legacy.lastSemester)
        assertEquals(courses, legacy.lastCourses)
        assertEquals(true, legacy.lastOverwrite)
    }

    @Test
    fun `commit forwards Conflict pending courses`() = runTest {
        val legacy = FakeLegacyImport(commitResult = ImportResult.Cancelled)
        val preparation = ImportPreparation.Conflict(
            semester = semester,
            existingCount = 1,
            newCount = courses.size,
            pendingCourses = courses,
        )

        val actual = CourseImportProviderAdapter(legacy).commit(preparation, overwrite = false)

        assertSame(ImportResult.Cancelled, actual)
        assertEquals(semester, legacy.lastSemester)
        assertEquals(courses, legacy.lastCourses)
        assertEquals(false, legacy.lastOverwrite)
    }

    private class FakeLegacyImport(
        private val commitResult: ImportResult = ImportResult.Cancelled,
    ) : CourseImportContract {
        var preparation: ImportPreparation = ImportPreparation.Ready(
            Semester(name = "fake", code = "fake"),
            emptyList(),
        )
        var lastHtml: String? = null
        var lastSemester: Semester? = null
        var lastCourses: List<Course>? = null
        var lastOverwrite: Boolean? = null

        override suspend fun prepareImport(html: String): ImportPreparation {
            lastHtml = html
            return preparation
        }

        override suspend fun commitImport(
            semester: Semester,
            courses: List<Course>,
            overwrite: Boolean,
        ): ImportResult {
            lastSemester = semester
            lastCourses = courses
            lastOverwrite = overwrite
            return commitResult
        }
    }
}
