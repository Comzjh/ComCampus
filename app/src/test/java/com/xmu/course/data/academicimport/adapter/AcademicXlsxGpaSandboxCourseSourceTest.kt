package com.xmu.course.data.academicimport.adapter

import com.xmu.course.contracts.CourseManagementContract
import com.xmu.course.contracts.TimetableManagementContract
import com.xmu.course.contracts.TimetableObservationContract
import com.xmu.course.contracts.TimetableSummary
import com.xmu.course.contracts.grades.GpaSandboxPrefillResult
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.data.academicimport.xlsx.AcademicXlsxRow
import com.xmu.course.data.academicimport.xlsx.TemporaryAcademicXlsxStore
import com.xmu.course.data.tron.TronCourseObservationContract
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AcademicXlsxGpaSandboxCourseSourceTest {
    @Test
    fun `matches current timetable against excel credits and removes duplicate timetable names`() = runTest {
        val file = File.createTempFile("academic-gpa-source", ".xlsx")
        try {
            TemporaryAcademicXlsxStore(file).writeAndRead(
                listOf(
                    AcademicXlsxRow(
                        courseName = "高等数学",
                        creditText = "4",
                        status = "COMPLETED",
                        confidence = "HIGH",
                    ),
                    AcademicXlsxRow(
                        courseName = "无关历史课程",
                        creditText = "2",
                        status = "COMPLETED",
                        confidence = "HIGH",
                    ),
                ),
            )

            val result = AcademicXlsxGpaSandboxCourseSource(
                xlsxStore = TemporaryAcademicXlsxStore(file),
                timetableRepository = FakeTimetableRepository(
                    TimetableFeatureState(
                        timetable = Timetable(id = 1L, name = "2026 秋", semesterId = 2L),
                        courses = listOf(
                            course(10L, "高等 数学"),
                            course(11L, "高等数学"),
                            course(12L, "本学期但无学分记录"),
                        ),
                    ),
                ),
            ).loadCurrentSemesterCourses()

            assertTrue(result.sourceAvailable)
            assertEquals("2026 秋", result.semesterName)
            assertEquals(1, result.duplicateTimetableCourseCount)
            assertEquals(listOf("高等 数学"), result.courses.map { it.courseName })
            assertEquals(listOf(4.0), result.courses.map { it.credit })
            assertEquals(listOf("本学期但无学分记录"), result.unmatchedTimetableCourseNames)
        } finally {
            assertTrue(file.delete())
        }
    }

    @Test
    fun `does not guess when excel has conflicting credits or review reason`() = runTest {
        val file = File.createTempFile("academic-gpa-source-conflict", ".xlsx")
        try {
            TemporaryAcademicXlsxStore(file).writeAndRead(
                listOf(
                    AcademicXlsxRow("课程 A", "3", status = "COMPLETED", confidence = "HIGH"),
                    AcademicXlsxRow("课程 A", "4", status = "COMPLETED", confidence = "HIGH"),
                    AcademicXlsxRow(
                        "课程 B",
                        "2",
                        status = "COMPLETED",
                        confidence = "HIGH",
                        reviewReason = "待确认",
                    ),
                ),
            )
            val result: GpaSandboxPrefillResult = AcademicXlsxGpaSandboxCourseSource(
                TemporaryAcademicXlsxStore(file),
                FakeTimetableRepository(
                    TimetableFeatureState(
                        timetable = Timetable(id = 1L, name = "2026 秋", semesterId = 2L),
                        courses = listOf(course(1L, "课程 A"), course(2L, "课程 B")),
                    ),
                ),
            ).loadCurrentSemesterCourses()

            assertTrue(result.courses.isEmpty())
            assertEquals(listOf("课程 A", "课程 B"), result.unmatchedTimetableCourseNames)
        } finally {
            assertTrue(file.delete())
        }
    }

    private fun course(id: Long, name: String) = Course(
        id = id,
        semesterId = 2L,
        name = name,
        dayOfWeek = 1,
        startSection = 1,
        duration = 2,
        weeks = setOf(1),
        source = CourseSource.IMPORT,
    )
}

private class FakeTimetableRepository(
    private val state: TimetableFeatureState,
) : TimetableFeatureRepository {
    override fun observeCurrentTimetableState(): Flow<TimetableFeatureState> = flowOf(state)
    override suspend fun addCourse(course: Course) = Unit
    override suspend fun updateCourseColor(courseId: Long, color: String) = Unit
    override suspend fun updateCourseNote(courseId: Long, note: String) = Unit
    override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) = Unit
    override suspend fun updateConfig(config: TimetableConfig) = Unit
}
