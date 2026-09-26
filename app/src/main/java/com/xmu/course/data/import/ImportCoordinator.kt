package com.xmu.course.data.import

import com.xmu.course.data.auth.WiseduSessionMarker
import com.xmu.course.contracts.TimetableImportContract
import com.xmu.course.data.import.adapter.JwCourseImportAdapter
import com.xmu.course.data.import.provider.CourseImportProvider
import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester

/**
 * 现有 Wisedu HTML 导入链路的 data-side 编排边界。
 *
 * 这里只组合已有 parser/repository/本地写入顺序，不新增导入规则。
 */
class ImportCoordinator(
    private val courseImportProvider: CourseImportProvider,
    private val timetableRepository: TimetableImportContract,
    private val sessionMarker: WiseduSessionMarker,
    private val jwCourseImportAdapter: JwCourseImportAdapter = JwCourseImportAdapter(),
) {
    suspend fun prepareImport(html: String): ImportPreparation =
        courseImportProvider.preview(jwCourseImportAdapter.adapt(html))

    suspend fun commitImport(
        semester: Semester,
        courses: List<Course>,
        overwrite: Boolean,
    ): ImportResult {
        val result = courseImportProvider.commit(
            preparation = ImportPreparation.Ready(semester = semester, courses = courses),
            overwrite = overwrite,
        )
        if (result is ImportResult.Success) {
            timetableRepository.ensureForSemester(
                result.semesterId,
                semester.name,
                semester.startDate,
            )
            sessionMarker.markVerified()
        }
        return result
    }

}
