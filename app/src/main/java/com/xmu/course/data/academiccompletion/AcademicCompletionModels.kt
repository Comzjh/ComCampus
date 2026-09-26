package com.xmu.course.data.academiccompletion

import java.math.BigDecimal

/**
 * 学业完成快照的内部持久化 schema 版本（本地 JSON 文件版本，不是 Room migration）。
 * 内部格式与来源（xywccx 快照）格式完全解耦，来源格式变化不影响此处。
 */
const val ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION: Int = 1

/** 来源侧学分状态：只描述来源快照里学分是否可得，本地确认绝不写回该字段。 */
enum class SourceXfStatus {
    /** 来源快照已给出确定学分（来源事实）。 */
    CONFIRMED,

    /** 来源暂不可得（方案外 + 在修的系统限制场景），需要用户手动确认。 */
    NEEDS_MANUAL,
}

/** 培养方案级摘要；学分一律使用规范化十进制字符串，不使用 Float/Double。 */
data class PlanSummary(
    val planName: String,
    val requiredCreditsText: String,
    val earnedCreditsText: String,
    val sourceSnapshotAt: String,
    val sourceThisSemesterTotalText: String,
)

/** 一门在修课程（来源事实，只读；任何本地确认都不修改本对象）。 */
data class SourceCourse(
    val courseCode: String,
    val courseName: String,
    val creditsText: String?,
    val status: SourceXfStatus,
    val inPlan: Boolean,
    val teacherNames: String,
    val classCode: String,
    val confirmationHint: String?,
)

/** 方案外已结课课程（来源事实；Phase 10.1 仅展示，不进入 Transcript/GPA 链路）。 */
data class CompletedCourseOutsidePlan(
    val courseCode: String,
    val courseName: String,
    val creditsText: String,
    val termCode: String,
    val scoreText: String,
)

/** 用户手动确认的学分；只存在于本地覆盖层。 */
data class LocalCreditOverride(
    val courseCode: String,
    val creditsText: String,
)

/**
 * 学业完成快照的内部模型。
 *
 * 刻意不包含姓名、学号等任何个人身份字段；
 * 来源事实（enrolledCourses）与本地确认（localOverrides）严格分层存储：
 * 展示顺序为 Source confirmed fact > local override，
 * 且 local override 绝不回写 source fact。
 */
data class AcademicCompletionSnapshot(
    val schemaVersion: Int,
    val semesterLabel: String,
    val plan: PlanSummary,
    val enrolledCourses: List<SourceCourse>,
    val completedCoursesOutsidePlan: List<CompletedCourseOutsidePlan>,
    val localOverrides: Map<String, LocalCreditOverride>,
    /** 本机成功获取并保存该快照的时间；旧版缓存没有此字段时为 null。 */
    val fetchedAtEpochMillis: Long? = null,
) {
    init {
        require(schemaVersion == ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION) {
            "unsupported internal schema: ${schemaVersion}"
        }
    }

    /** 仍需用户人工确认（NEEDS_MANUAL 且尚无本地覆盖）的课程。 */
    val pendingManualCourses: List<SourceCourse>
        get() = enrolledCourses.filter {
            it.status == SourceXfStatus.NEEDS_MANUAL && !localOverrides.containsKey(it.courseCode)
        }

    /** 展示层有效学分：来源优先，其次本地覆盖；两者皆无则为 null，绝不猜测。 */
    fun effectiveCreditsText(course: SourceCourse): String? =
        course.creditsText ?: localOverrides[course.courseCode]?.creditsText

    /** 该课是否处于"本地确认"状态（来源不可得 + 用户已填入）。 */
    fun isLocalConfirmed(course: SourceCourse): Boolean =
        course.status == SourceXfStatus.NEEDS_MANUAL && localOverrides.containsKey(course.courseCode)

    /** 本地补充分：仅统计实际命中 NEEDS_MANUAL 课程的覆盖值。 */
    fun localSupplementedTotalText(): String =
        CreditsDecimal.sum(
            localOverrides.values
                .filter { override ->
                    enrolledCourses.any {
                        it.courseCode == override.courseCode &&
                            it.status == SourceXfStatus.NEEDS_MANUAL &&
                            it.creditsText == null
                    }
                }
                .map { it.creditsText },
        )

    /** "含本地确认合计" = 来源确认总计 + 本地补充；与来源值在 UI 上明确区分。 */
    fun combinedTotalIncludingLocalText(): String =
        CreditsDecimal.sum(listOf(plan.sourceThisSemesterTotalText, localSupplementedTotalText()))

    val hasLocalSupplement: Boolean
        get() = localOverrides.isNotEmpty()
}

/**
 * 学分数值的规范化工具：统一走 BigDecimal 字符串，杜绝二进制浮点误差。
 *
 * 规范化 = 去除多余尾零（"2.0" -> "2"，"0.250" -> "0.25"）。
 */
internal object CreditsDecimal {

    /** 合法正十进制（>0）才返回规范化字符串；否则 null。 */
    fun normalizePositive(raw: String): String? =
        parse(raw)?.let { if (it.signum() <= 0) null else plain(it) }

    /** 允许 0 的规范化（用于已获学分等非负场景）。 */
    fun normalizeNonNegative(raw: String): String? =
        parse(raw)?.let { if (it.signum() < 0) null else plain(it) }

    fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    fun parse(raw: String): BigDecimal? = try {
        BigDecimal(raw.trim())
    } catch (error: NumberFormatException) {
        null
    }

    /** 任意符号的规范化十进制字符串；非法输入返回 null。 */
    fun parseDecimalText(raw: String): String? = parse(raw)?.let { plain(it) }

    fun sum(values: List<String>): String {
        var total = BigDecimal.ZERO
        values.forEach { total = total.add(BigDecimal(it)) }
        return plain(total)
    }
}
