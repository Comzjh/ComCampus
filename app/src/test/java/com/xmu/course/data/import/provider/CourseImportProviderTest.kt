package com.xmu.course.data.import.provider

import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult
import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CourseImportProviderTest {
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
    fun `preview receives source payload without provider details`() = runTest {
        val provider = FakeProvider(
            preparation = ImportPreparation.Ready(semester, courses),
        )

        val actual = provider.preview(CourseImportInput(rawHtml = "<table>courses</table>"))

        assertEquals(provider.preparation, actual)
        assertEquals("<table>courses</table>", provider.lastInput?.rawHtml)
    }

    @Test
    fun `commit forwards preparation and overwrite decision`() = runTest {
        val preparation = ImportPreparation.Conflict(
            semester = semester,
            existingCount = 1,
            newCount = courses.size,
            pendingCourses = courses,
        )
        val provider = FakeProvider(commitResult = ImportResult.Success(42L, 1))

        val actual = provider.commit(preparation, overwrite = true)

        assertEquals(ImportResult.Success(42L, 1), actual)
        assertSame(preparation, provider.lastPreparation)
        assertEquals(true, provider.lastOverwrite)
    }

    private class FakeProvider(
        val preparation: ImportPreparation = ImportPreparation.Ready(
            semester = Semester(name = "fake", code = "fake"),
            courses = emptyList(),
        ),
        private val commitResult: ImportResult = ImportResult.Cancelled,
    ) : CourseImportProvider {
        var lastInput: CourseImportInput? = null
            private set
        var lastPreparation: ImportPreparation? = null
            private set
        var lastOverwrite: Boolean? = null
            private set

        override suspend fun preview(input: CourseImportInput): ImportPreparation {
            lastInput = input
            return preparation
        }

        override suspend fun commit(
            preparation: ImportPreparation,
            overwrite: Boolean,
        ): ImportResult {
            lastPreparation = preparation
            lastOverwrite = overwrite
            return commitResult
        }
    }
}
