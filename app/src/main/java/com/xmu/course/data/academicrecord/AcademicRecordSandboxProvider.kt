package com.xmu.course.data.academicrecord

import com.xmu.course.data.academicrecord.adapter.AcademicRecordSandboxDraft
import com.xmu.course.data.academicrecord.adapter.AcademicRecordSandboxMapper

/** 读取已保存学业记录并生成 Sandbox 草稿，不触碰 UI 或 GPA 计算。 */
class AcademicRecordSandboxProvider(
    private val repository: AcademicRecordRepository,
    private val mapper: AcademicRecordSandboxMapper = AcademicRecordSandboxMapper,
) {
    suspend fun loadDrafts(): List<AcademicRecordSandboxDraft> =
        mapper.map(repository.getAll())
}
