package com.xmu.course.data.transcript.manual

import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.contracts.grades.TranscriptSource

/**
 * 用户手动录入的一条成绩事实。
 *
 * 全部字段来自用户显式输入，不自动猜测；手动数据不属于 AcademicImport，
 * 也不进入 academic_records。
 */
data class ManualTranscriptEntry(
    val courseName: String,
    val creditsText: String,
    val scoreText: String,
    val term: String,
) {
    init {
        require(courseName.isNotBlank()) { "courseName must not be blank" }
        require(creditsText.isNotBlank()) { "creditsText must not be blank" }
        require(scoreText.isNotBlank()) { "scoreText must not be blank" }
        require(term.isNotBlank()) { "term must not be blank" }
    }
}

/** 学分原文按显式十进制解析；解析失败保持 null，不做任何猜测。 */
fun ManualTranscriptEntry.toTranscriptCourse(): TranscriptCourse = TranscriptCourse(
    courseName = courseName,
    score = scoreText,
    credits = creditsText.trim().toDoubleOrNull(),
    creditsText = creditsText,
    gradeText = null,
    term = term,
    source = TranscriptSource.MANUAL,
)
