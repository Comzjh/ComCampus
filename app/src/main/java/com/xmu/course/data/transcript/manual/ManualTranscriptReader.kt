package com.xmu.course.data.transcript.manual

import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.contracts.grades.TranscriptSemester
import com.xmu.course.contracts.grades.TranscriptSnapshot
import com.xmu.course.contracts.grades.TranscriptSource

/**
 * 把手动录入条目聚合为只读快照。
 *
 * 每条课程的 term/source 独立保留；不合并、不推断任何成绩字段。
 */
class ManualTranscriptReader(
    private val repository: ManualTranscriptRepository,
) : TranscriptReader {

    override suspend fun getTranscript(): TranscriptSnapshot? {
        val entries = repository.entries.value
        if (entries.isEmpty()) return null

        return TranscriptSnapshot(
            semester = TranscriptSemester(name = "手动录入", code = null),
            courses = entries.map { it.toTranscriptCourse() },
            source = TranscriptSource.MANUAL,
        )
    }
}
