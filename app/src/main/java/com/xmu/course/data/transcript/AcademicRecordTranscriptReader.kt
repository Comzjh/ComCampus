package com.xmu.course.data.transcript

import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.contracts.grades.TranscriptSemester
import com.xmu.course.contracts.grades.TranscriptSnapshot
import com.xmu.course.data.academicrecord.AcademicRecordRepository

/**
 * 将已确认导入的 AcademicRecord 聚合为只读 TranscriptSnapshot。
 *
 * 不推断 score/grade/term；没有记录时返回 null（Unavailable）。
 */
class AcademicRecordTranscriptReader(
    private val repository: AcademicRecordRepository,
) : TranscriptReader {

    override suspend fun getTranscript(): TranscriptSnapshot? {
        val records = repository.getAll()
        if (records.isEmpty()) return null

        return TranscriptSnapshot(
            semester = TranscriptSemester(
                name = "导入成绩记录",
                code = null,
            ),
            courses = records.map { it.toTranscriptCourse() },
            source = com.xmu.course.contracts.grades.TranscriptSource.ACADEMIC_IMPORT,
        )
    }
}
