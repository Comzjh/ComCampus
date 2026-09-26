package com.xmu.course.ui.grades

import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.domain.grades.GradeInput
import com.xmu.course.domain.grades.GradePointScale

/**
 * 只读成绩单的 GPA 分析结果。
 *
 * 这是纯计算产物（Calculated Data），绝不写回 TranscriptCourse、AcademicRecord，
 * 也不持久化；仅用于概览展示。
 */
sealed interface TranscriptGpaAnalysis {
    /** 完全没有课程。 */
    data object Empty : TranscriptGpaAnalysis

    /** 有课程，但没有一门可换算（成绩缺失/学分缺失/成绩不在规则内）。 */
    data object Insufficient : TranscriptGpaAnalysis

    /**
     * 成功按学分加权计算。
     * @param includedCourses 参与计算的课程数
     * @param excludedCourses 因缺成绩/缺学分/无法换算而被跳过的课程数
     */
    data class Computed(
        val gpa: Double,
        val totalCredits: Double,
        val includedCourses: Int,
        val excludedCourses: Int,
    ) : TranscriptGpaAnalysis
}

/**
 * 把只读 TranscriptCourse 列表换算为绩点并按学分求加权平均。
 *
 * - 复用现有 GradePointScale，不内置任何学校默认规则。
 * - 成绩文本按显式规则判定：可解析为有限十进制视为百分制，否则按等级制交给规则换算。
 * - 无法换算或学分非正数的课程计入 excluded，不中断整体计算，也不猜测。
 * - 课程 source（导入/官方/手动）不参与计算，仅作为事实标签。
 */
object TranscriptGpaAnalyzer {
    fun analyze(courses: List<TranscriptCourse>, scale: GradePointScale): TranscriptGpaAnalysis {
        if (courses.isEmpty()) return TranscriptGpaAnalysis.Empty

        var weightedPoints = 0.0
        var totalCredits = 0.0
        var included = 0

        for (course in courses) {
            val credits = course.credits?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val input = gradeInputOf(course) ?: continue
            val points = runCatching { scale.pointsFor(input) }.getOrNull() ?: continue
            if (!points.isFinite()) continue

            weightedPoints += credits * points
            totalCredits += credits
            included++
        }

        if (included == 0 || totalCredits <= 0.0) {
            return TranscriptGpaAnalysis.Insufficient
        }

        return TranscriptGpaAnalysis.Computed(
            gpa = weightedPoints / totalCredits,
            totalCredits = totalCredits,
            includedCourses = included,
            excludedCourses = courses.size - included,
        )
    }

    private fun gradeInputOf(course: TranscriptCourse): GradeInput? {
        val score = course.score?.trim()
        if (!score.isNullOrEmpty()) {
            score.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return GradeInput.Percentage(it) }
            return GradeInput.Letter(score)
        }
        val gradeText = course.gradeText?.trim()
        if (!gradeText.isNullOrEmpty()) return GradeInput.Letter(gradeText)
        return null
    }
}
