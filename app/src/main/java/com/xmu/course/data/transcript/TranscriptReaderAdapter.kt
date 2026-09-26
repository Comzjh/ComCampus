package com.xmu.course.data.transcript

import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.contracts.grades.TranscriptSemester
import com.xmu.course.contracts.grades.TranscriptSnapshot as ContractTranscriptSnapshot

/**
 * 将现有本地 Transcript 读取模型接到 Grades Contract 的最小 seam。
 *
 * 当前默认没有数据源，返回 null；未来 provider 只需通过 loadSnapshot 注入，
 * 不需要让 Grades feature 知道 URL、Cookie、DTO 或认证流程。
 */
class TranscriptReaderAdapter(
    private val loadSnapshot: suspend () -> TranscriptSnapshot? = { null },
) : TranscriptReader {

    override suspend fun getTranscript(): ContractTranscriptSnapshot? =
        loadSnapshot()?.let { snapshot ->
            ContractTranscriptSnapshot(
                semester = TranscriptSemester(
                    name = snapshot.semester.name,
                    code = snapshot.semester.code,
                ),
                courses = snapshot.courses.map { course ->
                    TranscriptCourse(
                        courseName = course.courseName,
                        score = course.score,
                        credits = course.credits,
                    )
                },
            )
        }
}
