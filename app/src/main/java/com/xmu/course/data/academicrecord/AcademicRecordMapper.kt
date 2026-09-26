package com.xmu.course.data.academicrecord

import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse
import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource

/**
 * 将用户已确认的导入课程转换为长期学业事实。
 *
 * 只接受确认后的合约，避免解析候选或审核元数据进入 AcademicRecord。
 */
object AcademicRecordMapper {
    fun fromConfirmed(
        course: ConfirmedAcademicCourse,
        source: AcademicRecordSource,
    ): AcademicRecord = AcademicRecord(
        name = course.name,
        creditsText = course.creditText,
        source = source,
    )

    fun fromConfirmed(
        courses: List<ConfirmedAcademicCourse>,
        source: AcademicRecordSource,
    ): List<AcademicRecord> = courses.map { fromConfirmed(it, source) }
}
