package com.xmu.course.data.transcript

/**
 * Grades / Transcript 的只读数据边界。
 *
 * 当前导入链路尚未提供成绩数据，因此成绩相关字段保持 null，不由本模型推断。
 */
data class TranscriptSnapshot(
    val semester: SemesterRef,
    val courses: List<CourseTranscriptItem>,
)

data class SemesterRef(
    val name: String,
    val code: String?,
)

data class CourseTranscriptItem(
    val courseName: String,
    val teacher: String?,
    val location: String?,
    val score: String? = null,
    val credits: Double? = null,
)
