package com.xmu.course.ui.academic

import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.domain.grades.JwDerivedGpa
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 学业模块的展示模型与纯聚合函数。
 *
 * 聚合层零 Android 依赖：来源事实（xywccx/gsapp/cjcx 快照）到 UI 模型的映射
 * 全部是确定性纯函数，JVM 直测；本层绝不修改任何来源快照。
 */

/** 学业概览：已修/要求口径一律以培养方案（xywccx）为权威，禁止与成绩记录互相补齐。 */
data class AcademicOverviewRow(
    val planName: String,
    val requiredCreditsText: String,
    val earnedCreditsText: String,
    /** 要求-已修；无法解析或为负时为 null（不猜测、不显示负数）。 */
    val remainingCreditsText: String?,
    /** 进度环比例；分母不可得时 null。 */
    val progressFraction: Float?,
    val enrolledCount: Int,
    val pendingManualCount: Int,
    val semesterLabel: String,
    val outsidePlanCompletedCount: Int,
    val sourceSnapshotAt: String,
)

/** 历史成绩的一个学期分组摘要行。 */
data class AcademicSemesterRow(
    val semesterCode: String,
    val semesterDisplay: String,
    val courseCount: Int,
    val creditsText: String,
    /** 按官方绩点加权的学期均值；无可用绩点行时为 null。标注为本地计算，非官方 GPA。 */
    val officialPointsAverageText: String?,
    val hasOutsidePlanCourse: Boolean,
)

/** 历史成绩明细行（官方文本原样呈现）。 */
data class AcademicHistoryCourse(
    val courseName: String,
    val creditsText: String,
    val gradeText: String,
    val pointGradeText: String?,
    val courseNatureDisplay: String?,
    val outsidePlan: Boolean,
)

data class AcademicHistorySemester(
    val row: AcademicSemesterRow,
    val courses: List<AcademicHistoryCourse>,
)

enum class AcademicGpaTone { NO_DATA, NEEDS_CONFIRMATION, INSUFFICIENT, COMPUTED }

/** GPA 呈现语义：只有 COMPUTED 才出数；NEEDS_CONFIRMATION 绝不出默认结论。 */
data class AcademicGpaSummary(
    val tone: AcademicGpaTone,
    val valueText: String? = null,
    val countedCourses: Int = 0,
    val countedCreditsText: String? = null,
    val pointFreeCourses: Int = 0,
    val excludedByPolicy: Int = 0,
    val malformedCourses: Int = 0,
    val candidateCount: Int = 0,
    /** false = 培养方案尚未同步：无法核对方案归属，展示层必须如实标注。 */
    val planSynced: Boolean = true,
)

object AcademicModuleAggregator {

    /** 方案外课程代码集合 = 方案外已结课 ∪ 在修且不在方案内（来源事实叠加，不做名称模糊匹配）。 */
    fun outsidePlanCourseCodes(snapshot: AcademicCompletionSnapshot?): Set<String> {
        snapshot ?: return emptySet()
        return buildSet {
            snapshot.completedCoursesOutsidePlan.forEach { add(it.courseCode) }
            snapshot.enrolledCourses.filterNot { it.inPlan }.forEach { add(it.courseCode) }
        }
    }

    fun overview(snapshot: AcademicCompletionSnapshot?): AcademicOverviewRow? {
        snapshot ?: return null
        val plan = snapshot.plan
        val required = parseDecimal(plan.requiredCreditsText)
        val earned = parseDecimal(plan.earnedCreditsText)
        val remaining = if (required != null && earned != null) {
            (required - earned).takeIf { it.signum() >= 0 }?.stripTrailingZeros()?.toPlainString()
        } else {
            null
        }
        val fraction = if (required != null && required.signum() > 0 && earned != null) {
            earned.divide(required, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
        return AcademicOverviewRow(
            planName = plan.planName,
            requiredCreditsText = plan.requiredCreditsText,
            earnedCreditsText = plan.earnedCreditsText,
            remainingCreditsText = remaining,
            progressFraction = fraction,
            enrolledCount = snapshot.enrolledCourses.size,
            pendingManualCount = snapshot.pendingManualCourses.size,
            semesterLabel = snapshot.semesterLabel,
            outsidePlanCompletedCount = snapshot.completedCoursesOutsidePlan.size,
            sourceSnapshotAt = plan.sourceSnapshotAt,
        )
    }

    fun historySemesters(grade: JwGradeSnapshot?, outsideCodes: Set<String>): List<AcademicHistorySemester> {
        grade ?: return emptyList()
        return grade.groupedBySemester().map { (code, entries) ->
            AcademicHistorySemester(
                row = AcademicSemesterRow(
                    semesterCode = code,
                    semesterDisplay = entries.first().semesterDisplay.ifBlank { code },
                    courseCount = entries.size,
                    creditsText = sumCredits(entries),
                    officialPointsAverageText = averageOfficialPoints(entries),
                    hasOutsidePlanCourse = entries.any { it.courseCode in outsideCodes },
                ),
                courses = entries.map { entry ->
                    AcademicHistoryCourse(
                        courseName = entry.courseName,
                        creditsText = entry.creditsText,
                        gradeText = entry.gradeText,
                        pointGradeText = entry.pointGradeText,
                        courseNatureDisplay = entry.courseNatureDisplay ?: entry.courseCategoryDisplay,
                        outsidePlan = entry.courseCode in outsideCodes,
                    )
                },
            )
        }
    }

    fun gpaSummary(
        grade: JwGradeSnapshot?,
        outsideCodes: Set<String>,
        policy: OutsidePlanGpaPolicy,
        planSynced: Boolean,
    ): AcademicGpaSummary {
        val snapshot = grade
            ?: return AcademicGpaSummary(tone = AcademicGpaTone.NO_DATA, planSynced = planSynced)
        return when (
            val result = JwDerivedGpa.compute(
                courses = snapshot.entries.map { entry ->
                    JwDerivedGpa.Course(
                        creditsText = entry.creditsText,
                        pointGradeText = entry.pointGradeText,
                        outsidePlan = entry.courseCode in outsideCodes,
                    )
                },
                policy = policy,
            )
        ) {
            is JwDerivedGpa.Result.Computed -> AcademicGpaSummary(
                tone = AcademicGpaTone.COMPUTED,
                valueText = result.gpaText,
                countedCourses = result.countedCourses,
                countedCreditsText = result.countedCreditsText,
                pointFreeCourses = result.pointFreeCourses,
                excludedByPolicy = result.excludedByPolicy,
                malformedCourses = result.malformedCourses,
                planSynced = planSynced,
            )
            is JwDerivedGpa.Result.NeedsConfirmation -> AcademicGpaSummary(
                tone = AcademicGpaTone.NEEDS_CONFIRMATION,
                candidateCount = result.candidateCount,
                planSynced = planSynced,
            )
            JwDerivedGpa.Result.Insufficient -> AcademicGpaSummary(
                tone = AcademicGpaTone.INSUFFICIENT,
                planSynced = planSynced,
            )
        }
    }

    private fun sumCredits(entries: List<JwGradeEntry>): String {
        var total = BigDecimal.ZERO
        for (entry in entries) {
            parseDecimal(entry.creditsText)?.let { total += it }
        }
        return total.stripTrailingZeros().toPlainString()
    }

    private fun averageOfficialPoints(entries: List<JwGradeEntry>): String? {
        var weighted = BigDecimal.ZERO
        var credits = BigDecimal.ZERO
        for (entry in entries) {
            val credit = parseDecimal(entry.creditsText)?.takeIf { it.signum() > 0 } ?: continue
            val point = entry.pointGradeText?.trim()?.let { raw ->
            parseDecimal(raw)?.takeIf { it.signum() >= 0 && !raw.equals("N/A", ignoreCase = true) }
            } ?: continue
            weighted += point.multiply(credit)
            credits += credit
        }
        if (credits.signum() <= 0) return null
        return weighted.divide(credits, 2, RoundingMode.HALF_UP).toPlainString()
    }

    private fun parseDecimal(text: String): BigDecimal? = try {
        BigDecimal(text.trim())
    } catch (error: NumberFormatException) {
        null
    }
}

/** 本机刷新时间展示（仅本地时间戳；官方统计时间另有字段，两者绝不混用）。 */
internal fun formatAcademicRefreshTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
