package com.xmu.course.data.academicimport.adapter

import com.xmu.course.contracts.academicimport.AcademicImportDecision
import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse

/** Academic Import 到 GPA Sandbox 草稿输入的单向适配器。 */
class AcademicImportSandboxMapper {
    fun map(decision: AcademicImportDecision): List<AcademicSandboxCourseInput> = when (decision) {
        is AcademicImportDecision.Confirmed -> decision.courses.map(ConfirmedAcademicCourse::toSandboxInput)
        AcademicImportDecision.Cancelled -> emptyList()
    }
}

/**
 * Sandbox 可编辑的课程草稿：学分仍是用户可见的原始文本，成绩不由导入来源填写。
 * 该模型不代表官方成绩，也不携带持久化标识。
 */
data class AcademicSandboxCourseInput(
    val name: String,
    val credits: String,
    val grade: String = "",
)

private fun ConfirmedAcademicCourse.toSandboxInput() = AcademicSandboxCourseInput(
    name = name,
    credits = creditText,
)
