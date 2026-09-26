package com.xmu.course.data.jwgrades

/**
 * cjcx 成绩单本地模型。
 *
 * 只保留官方原始文本字段（成绩/绩点/学分按官方呈现值存储），
 * 不做 GPA / 字母等级换算；绝不包含学号、姓名等身份字段。
 */
data class JwGradeEntry(
    /** 稳定行标识：优先官方 WID；缺失时使用确定性回退键。 */
    val rowId: String,
    /** XNXQDM 学期代码原文。 */
    val semesterCode: String,
    /** XNXQDM_DISPLAY 学期显示名（可空结构下用空串代替 null 简化 UI）。 */
    val semesterDisplay: String,
    /** KCH 课程号。 */
    val courseCode: String,
    /** KCM 课程名。 */
    val courseName: String,
    /** XF 学分（官方数值原文，不做格式化改写）。 */
    val creditsText: String,
    /** ZCJ 总成绩官方文本（数值或等级文字均按原文保留）。 */
    val gradeText: String,
    /** XFJD 绩点字符串；合格制课程为 "N/A"（计学分不计绩点）。 */
    val pointGradeText: String?,
    /** KCXZDM_DISPLAY 课程性质显示。 */
    val courseNatureDisplay: String?,
    /** KCLBDM_DISPLAY 课程类别显示。 */
    val courseCategoryDisplay: String?,
    /** KKDWDM_DISPLAY 开课单位显示。 */
    val offeringUnitDisplay: String?,
    /** CXCKDM 重修/补考标记原文。 */
    val retakeCode: String?,
)

/**
 * 一次成功刷新的完整成绩单缓存快照（含来源标注）。
 *
 * provenance 仅含 provider/能力/时间戳：不存 URL、query、会话或任何凭据。
 */
data class JwGradeSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val refreshedAtEpochMillis: Long,
    val providerId: String,
    val sourceCapability: String,
    /** 全量学分合计（各课程 XF 的十进制求和原文）。 */
    val totalCreditsText: String,
    val entries: List<JwGradeEntry>,
) {
    /** 按学期分组（保持服务端 -XNXQDM 排序的学期首现顺序）。 */
    fun groupedBySemester(): List<Pair<String, List<JwGradeEntry>>> {
        val groups = LinkedHashMap<String, MutableList<JwGradeEntry>>()
        for (entry in entries) {
            groups.getOrPut(entry.semesterCode) { mutableListOf() }.add(entry)
        }
        return groups.entries.map { it.key to it.value.toList() }
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
