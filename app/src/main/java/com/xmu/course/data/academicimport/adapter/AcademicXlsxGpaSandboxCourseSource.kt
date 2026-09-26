package com.xmu.course.data.academicimport.adapter

import com.xmu.course.contracts.grades.GpaSandboxCourseDraft
import com.xmu.course.contracts.grades.GpaSandboxCourseSource
import com.xmu.course.contracts.grades.GpaSandboxPrefillResult
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.data.academicimport.xlsx.AcademicXlsxRow
import com.xmu.course.data.academicimport.xlsx.TemporaryAcademicXlsxStore
import com.xmu.course.domain.normalizeCourseName
import kotlinx.coroutines.flow.first

/**
 * 将临时 Excel 学分数据与当前课表组合成 GPA Sandbox 草稿。
 *
 * 这是 Academic Import 到 Grades 的 adapter：Grades 不知道 Excel、PDF 或课表存储实现。
 * 当前课表只提供课程名，课程学分必须来自 Excel；任何歧义都不会自动填入。
 */
class AcademicXlsxGpaSandboxCourseSource(
    private val xlsxStore: TemporaryAcademicXlsxStore,
    private val timetableRepository: TimetableFeatureRepository,
) : GpaSandboxCourseSource {
    override suspend fun loadCurrentSemesterCourses(): GpaSandboxPrefillResult {
        val document = xlsxStore.readIfExists()
            ?: return GpaSandboxPrefillResult(
                semesterName = null,
                courses = emptyList(),
                duplicateTimetableCourseCount = 0,
                unmatchedTimetableCourseNames = emptyList(),
                sourceAvailable = false,
            )
        val timetableState = timetableRepository.observeCurrentTimetableState().first()
        val uniqueTimetableCourses = linkedMapOf<String, String>()
        var duplicateCount = 0
        timetableState.courses.forEach { course ->
            val key = normalizeCourseName(course.name)
            if (key.isBlank()) return@forEach
            if (uniqueTimetableCourses.putIfAbsent(key, course.name.trim()) != null) {
                duplicateCount += 1
            }
        }

        val excelByName = document.rows
            .asSequence()
            .filter { it.status == COMPLETED_STATUS }
            .filter { it.reviewReason.isNullOrBlank() }
            .mapNotNull { row -> row.toValidExcelCourse() }
            .groupBy { normalizeCourseName(it.courseName) }

        val matched = mutableListOf<GpaSandboxCourseDraft>()
        val unmatched = mutableListOf<String>()
        uniqueTimetableCourses.forEach { (key, timetableName) ->
            val candidates = excelByName[key].orEmpty()
            val credits = candidates.map { it.credit }.distinct()
            when {
                credits.size == 1 -> matched += GpaSandboxCourseDraft(timetableName, credits.single())
                else -> unmatched += timetableName
            }
        }

        return GpaSandboxPrefillResult(
            semesterName = timetableState.timetable?.name,
            courses = matched,
            duplicateTimetableCourseCount = duplicateCount,
            unmatchedTimetableCourseNames = unmatched,
            sourceAvailable = true,
        )
    }

    private data class ValidExcelCourse(
        val courseName: String,
        val credit: Double,
    )

    private fun AcademicXlsxRow.toValidExcelCourse(): ValidExcelCourse? {
        val name = courseName?.trim()?.takeIf(String::isNotBlank) ?: return null
        val credit = creditText?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        return ValidExcelCourse(name, credit)
    }

    private companion object {
        const val COMPLETED_STATUS = "COMPLETED"
    }
}
