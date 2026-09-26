package com.xmu.course.data.transcript

import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.contracts.grades.TranscriptSource

/**
 * 将用户确认导入的 [AcademicRecord] 映射为只读 [TranscriptCourse]。
 *
 * 规则：只复制事实字段；score/grade/term 缺失就是 null，禁止根据课程名、
 * 学分或其他启发式信息推断成绩。
 */
fun AcademicRecord.toTranscriptCourse(): TranscriptCourse = TranscriptCourse(
    courseName = name,
    score = null,
    credits = null,
    creditsText = creditsText,
    gradeText = null,
    term = null,
    source = TranscriptSource.ACADEMIC_IMPORT,
)
