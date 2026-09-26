package com.xmu.course.contracts.grades

/**
 * Grades feature 的最小只读成绩读取能力。
 *
 * 实现可以暂时返回 null，表示当前没有可用成绩数据；Contract 不暴露学校系统、
 * 网络、认证或存储细节。
 */
interface TranscriptReader {
    suspend fun getTranscript(): TranscriptSnapshot?
}

/** Transcript 数据来源：只描述事实来源，不描述计算结果。 */
enum class TranscriptSource {
    ACADEMIC_IMPORT,
    OFFICIAL_TRANSCRIPT,
    MANUAL,
}

/** Grades feature 使用的只读成绩快照，不是本地成绩单存储实体。 */
data class TranscriptSnapshot(
    val semester: TranscriptSemester,
    val courses: List<TranscriptCourse>,
    /** 数据来源；null 表示未声明（保持旧调用兼容）。 */
    val source: TranscriptSource? = null,
    /** 快照所属学期描述；null 表示未声明。 */
    val term: String? = null,
)

data class TranscriptSemester(
    val name: String,
    val code: String?,
)

/** 成绩保持为不透明字符串，避免在 Contract 层臆测学校评分规则。 */
data class TranscriptCourse(
    val courseName: String,
    val score: String?,
    val credits: Double?,
    /** 导入事实中的学分原文；与 [credits] 并存，避免文本→数值转换造成信息损失。 */
    val creditsText: String? = null,
    /** 等级制成绩原文；缺失就是 null，禁止推断。 */
    val gradeText: String? = null,
    /** 学期描述；缺失就是 null，禁止推断。 */
    val term: String? = null,
    /** 来源；null 表示未声明（保持旧调用兼容）。 */
    val source: TranscriptSource? = null,
)