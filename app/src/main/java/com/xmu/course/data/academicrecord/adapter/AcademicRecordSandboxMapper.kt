package com.xmu.course.data.academicrecord.adapter

import com.xmu.course.contracts.academicrecord.AcademicRecord

/** 将已保存的学业课程转换为可编辑的 Sandbox 草稿，不触发计算或持久化。 */
object AcademicRecordSandboxMapper {
    fun map(record: AcademicRecord): AcademicRecordSandboxDraft = AcademicRecordSandboxDraft(
        name = record.name,
        credits = record.creditsText,
    )

    fun map(records: List<AcademicRecord>): List<AcademicRecordSandboxDraft> = records.map(::map)
}

/**
 * data 层到 Sandbox 组合层的最小草稿模型。
 * source、Room id 和导入元数据不进入用户的 GPA 模拟输入。
 */
data class AcademicRecordSandboxDraft(
    val name: String,
    val credits: String,
    val grade: String = "",
)
