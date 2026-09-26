package com.xmu.course.domain.grades

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 本地派生 GPA：Σ(官方绩点 × 学分) / Σ(参与课程学分)。
 *
 * 绩点一律取官方 XFJD 文本，App 不做分数→绩点换算；结果只能标注「本地计算」，
 * 永远不得冒充「官方 GPA」。方案外三态策略仅影响本计算，不改动任何官方数据。
 */
object JwDerivedGpa {

    /** 参与计算的一行官方成绩（字符串口径，来自 cjcx 快照原文）。 */
    data class Course(
        val creditsText: String,
        /** 官方绩点原文；"N/A" 表示合格制（计学分不计绩点），空白/非法视为异常。 */
        val pointGradeText: String?,
        /** 官方培养方案是否将其标记为方案外（来源事实叠加，不做本地猜测）。 */
        val outsidePlan: Boolean,
    )

    sealed interface Result {
        /**
         * @param gpaText 两位小数（HALF_UP）的本地计算 GPA。
         * @param pointFreeCourses 官方绩点 N/A：计学分不计绩点，被移出计算。
         * @param excludedByPolicy 按用户策略移出的方案外课程数（仅 EXCLUDE 时 >0）。
         * @param malformedCourses 学分/绩点字段异常、无法参与任何口径的课程数（绝不代猜）。
         */
        data class Computed(
            val gpaText: String,
            val countedCourses: Int,
            val countedCreditsText: String,
            val pointFreeCourses: Int,
            val excludedByPolicy: Int,
            val malformedCourses: Int,
        ) : Result

        /** 存在方案外候选且策略未确认：用户裁决前不出任何结论。 */
        data class NeedsConfirmation(val candidateCount: Int) : Result

        /** 没有任何可参与绩点计算的课程。 */
        data object Insufficient : Result
    }

    fun compute(courses: List<Course>, policy: OutsidePlanGpaPolicy): Result {
        var counted = 0
        var excluded = 0
        var pointFree = 0
        var malformed = 0
        var candidates = 0
        var weightedSum = BigDecimal.ZERO
        var creditsTotal = BigDecimal.ZERO

        for (course in courses) {
            val credits = parsePositiveCredits(course.creditsText)
            val points = parsePoints(course.pointGradeText)
            when {
                credits != null && points != null -> {
                    if (course.outsidePlan) {
                        candidates += 1
                        if (policy == OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN) {
                            excluded += 1
                            continue
                        }
                    }
                    counted += 1
                    creditsTotal += credits
                    weightedSum += points.multiply(credits)
                }
                credits != null && isPointFree(course.pointGradeText) -> pointFree += 1
                else -> malformed += 1
            }
        }

        if (candidates > 0 && policy == OutsidePlanGpaPolicy.UNCONFIRMED) {
            return Result.NeedsConfirmation(candidateCount = candidates)
        }
        if (counted == 0 || creditsTotal.signum() <= 0) return Result.Insufficient
        val gpa = weightedSum.divide(creditsTotal, 2, RoundingMode.HALF_UP)
        return Result.Computed(
            gpaText = gpa.toPlainString(),
            countedCourses = counted,
            countedCreditsText = creditsTotal.stripTrailingZeros().toPlainString(),
            pointFreeCourses = pointFree,
            excludedByPolicy = excluded,
            malformedCourses = malformed,
        )
    }

    private fun parsePositiveCredits(creditsText: String): BigDecimal? = try {
        BigDecimal(creditsText.trim()).stripTrailingZeros().takeIf { it.signum() > 0 }
    } catch (error: NumberFormatException) {
        null
    }

    private fun parsePoints(raw: String?): BigDecimal? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            BigDecimal(trimmed).takeIf { it.signum() >= 0 }
        } catch (error: NumberFormatException) {
            null
        }
    }

    private fun isPointFree(raw: String?): Boolean =
        raw != null && raw.trim().equals("N/A", ignoreCase = true)
}
