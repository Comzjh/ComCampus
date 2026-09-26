package com.xmu.course.contracts.academicrecord

/**
 * 已确认的学业课程事实，供后续存储与 Feature adapter 使用。
 *
 * 该合约只描述业务事实，不携带文件、解析会话、Provider 或 Android 类型。
 */
data class AcademicRecord(
    val name: String,
    val creditsText: String,
    val source: AcademicRecordSource,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(creditsText.isNotBlank()) { "creditsText must not be blank" }
    }
}

/** 长期记录的有限来源语义，不保存具体文件或网络元数据。 */
enum class AcademicRecordSource {
    JW_REPORT,
    MANUAL_IMPORT,
}
