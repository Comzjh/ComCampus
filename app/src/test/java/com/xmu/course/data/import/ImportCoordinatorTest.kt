package com.xmu.course.data.import

import com.xmu.course.data.auth.WiseduSessionMarker
import com.xmu.course.contracts.TimetableImportContract
import com.xmu.course.data.import.provider.CourseImportInput
import com.xmu.course.data.import.provider.CourseImportProvider
import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester
import com.xmu.course.domain.Timetable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ImportCoordinatorTest {
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
    fun `prepare delegates to injected course provider`() = runTest {
        val events = mutableListOf<String>()
        val expected = ImportPreparation.Ready(semester, courses)
        val course = FakeCourseImportProvider(events, preparation = expected)

        val actual = coordinator(course = course, events = events).prepareImport("html")

        assertEquals(expected, actual)
        assertEquals(listOf("prepare"), events)
        assertEquals("html", course.lastInput?.rawHtml)
    }

    @Test
    fun `successful commit binds timetable before marking session`() = runTest {
        val events = mutableListOf<String>()
        val timetable = FakeTimetableImportContract(events)
        val marker = FakeMarker(events)

        val result = coordinator(
            course = FakeCourseImportProvider(events, commitResult = ImportResult.Success(42L, 1)),
            timetable = timetable,
            marker = marker,
            events = events,
        ).commitImport(semester, courses, overwrite = true)

        assertEquals(ImportResult.Success(42L, 1), result)
        assertEquals(listOf("commit", "ensure:42", "mark"), events)
        assertEquals(42L, timetable.lastSemesterId)
    }

    @Test
    fun `cancelled commit does not bind timetable or mark session`() = runTest {
        val events = mutableListOf<String>()

        val result = coordinator(
            course = FakeCourseImportProvider(events, commitResult = ImportResult.Cancelled),
            events = events,
        ).commitImport(semester, courses, overwrite = false)

        assertSame(ImportResult.Cancelled, result)
        assertEquals(listOf("commit"), events)
    }

    @Test
    fun `repository exception propagates without downstream calls`() = runTest {
        val events = mutableListOf<String>()
        val expected = IllegalStateException("commit failed")
        val course = FakeCourseImportProvider(events, commitFailure = expected)

        try {
            coordinator(course = course, events = events).commitImport(semester, courses, overwrite = false)
            fail("expected commit failure")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }

        assertEquals(listOf("commit"), events)
    }

    private fun coordinator(
        course: CourseImportProvider,
        timetable: TimetableImportContract = FakeTimetableImportContract(mutableListOf()),
        marker: WiseduSessionMarker = FakeMarker(mutableListOf()),
        events: MutableList<String>,
    ) = ImportCoordinator(course, timetable, marker)

    private class FakeCourseImportProvider(
        private val events: MutableList<String>,
        private val preparation: ImportPreparation = ImportPreparation.Ready(
            Semester(name = "fake", code = "fake"),
            emptyList(),
        ),
        private val commitResult: ImportResult = ImportResult.Success(42L, 1),
        private val commitFailure: Throwable? = null,
    ) : CourseImportProvider {
        var lastInput: CourseImportInput? = null
        var lastPreparation: ImportPreparation? = null
        var lastOverwrite: Boolean? = null

        override suspend fun preview(input: CourseImportInput): ImportPreparation {
            events += "prepare"
            lastInput = input
            return preparation
        }

        override suspend fun commit(
            preparation: ImportPreparation,
            overwrite: Boolean,
        ): ImportResult {
            events += "commit"
            lastPreparation = preparation
            lastOverwrite = overwrite
            commitFailure?.let { throw it }
            return commitResult
        }
    }

    private class FakeTimetableImportContract(
        private val events: MutableList<String>,
    ) : TimetableImportContract {
        var lastSemesterId: Long? = null

        override suspend fun ensureForSemester(
            semesterId: Long,
            name: String,
            startDate: String?,
        ): Timetable {
            events += "ensure:$semesterId"
            lastSemesterId = semesterId
            return Timetable(id = 7L, name = name, semesterId = semesterId, startDate = startDate)
        }
    }

    private class FakeMarker(
        private val events: MutableList<String>,
    ) : WiseduSessionMarker {
        override fun isVerified(): Boolean = false

        override fun markVerified() {
            events += "mark"
        }

        override fun clear() = Unit
    }
}
