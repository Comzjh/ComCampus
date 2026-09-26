package com.xmu.course.data.import

import com.xmu.course.data.auth.WiseduSessionMarker
import com.xmu.course.contracts.TimetableImportContract
import com.xmu.course.data.import.provider.CourseImportInput
import com.xmu.course.data.import.provider.CourseImportProvider
import com.xmu.course.domain.Semester
import com.xmu.course.domain.Timetable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class JwCourseImportPipelineTest {
    @Test
    fun `html reaches provider through the JW adapter boundary`() = runTest {
        val provider = RecordingProvider()
        val coordinator = ImportCoordinator(
            courseImportProvider = provider,
            timetableRepository = NoOpTimetableImportContract(),
            sessionMarker = NoOpWiseduSessionMarker(),
        )

        coordinator.prepareImport("<html>jw-timetable</html>")

        assertEquals("<html>jw-timetable</html>", provider.lastInput?.rawHtml)
    }

    private class RecordingProvider : CourseImportProvider {
        var lastInput: CourseImportInput? = null

        override suspend fun preview(input: CourseImportInput): ImportPreparation {
            lastInput = input
            return ImportPreparation.Ready(
                semester = Semester(name = "test", code = "test"),
                courses = emptyList(),
            )
        }

        override suspend fun commit(
            preparation: ImportPreparation,
            overwrite: Boolean,
        ): ImportResult = ImportResult.Cancelled
    }

    private class NoOpTimetableImportContract : TimetableImportContract {
        override suspend fun ensureForSemester(
            semesterId: Long,
            name: String,
            startDate: String?,
        ): Timetable = Timetable(
            id = 1L,
            name = name,
            semesterId = semesterId,
            startDate = startDate,
        )
    }

    private class NoOpWiseduSessionMarker : WiseduSessionMarker {
        override fun isVerified(): Boolean = false

        override fun markVerified() = Unit

        override fun clear() = Unit
    }
}
