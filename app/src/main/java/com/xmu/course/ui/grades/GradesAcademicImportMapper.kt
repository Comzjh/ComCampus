package com.xmu.course.ui.grades

import com.xmu.course.data.academicrecord.adapter.AcademicRecordSandboxDraft

/** 将已准备好的学业草稿转换为 Sandbox 会话内可编辑课程，不触发读取或计算。 */
class GradesAcademicImportMapper {
    private var nextCourseId = 1L

    fun map(draft: AcademicRecordSandboxDraft): SimulatedCourseInputState =
        SimulatedCourseInputState(
            key = "$CUSTOM_COURSE_PREFIX$nextCourseId",
            id = nextCourseId++,
            name = draft.name,
            credits = draft.credits,
            grade = "",
        )

    fun map(drafts: List<AcademicRecordSandboxDraft>): List<SimulatedCourseInputState> =
        drafts.map(::map)
}
