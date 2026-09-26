package com.xmu.course.data.academicrecord

import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse
import com.xmu.course.contracts.academicrecord.AcademicRecordSource

/** 将用户已确认的课程保存为长期 AcademicRecord；不接收候选、文件或审核元数据。 */
class AcademicRecordImportSaver(
    private val repository: AcademicRecordRepository,
    private val mapper: AcademicRecordMapper = AcademicRecordMapper,
    private val source: AcademicRecordSource = AcademicRecordSource.JW_REPORT,
) {
    suspend fun saveConfirmed(courses: List<ConfirmedAcademicCourse>): Int {
        if (courses.isEmpty()) return 0

        mapper.fromConfirmed(courses, source).forEach { record ->
            repository.save(record)
        }
        return courses.size
    }
}
