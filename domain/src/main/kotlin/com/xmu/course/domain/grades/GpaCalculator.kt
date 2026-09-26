package com.xmu.course.domain.grades

/** 用户手动填写的当前 GPA 基线，不是成绩单数据。 */
data class GpaBaseline(
    val gpa: Double,
    val credits: Double,
) {
    init {
        require(gpa.isFinite() && gpa >= 0.0) { "当前 GPA 必须是非负有限数值" }
        require(credits.isFinite() && credits >= 0.0) { "已修学分必须是非负有限数值" }
    }
}

/** GPA Sandbox 中的一门手动模拟课程。 */
data class SimulatedCourse(
    val name: String,
    val credits: Double,
    val grade: GradeInput,
) {
    init {
        require(name.isNotBlank()) { "模拟课程名称不能为空" }
        require(credits.isFinite() && credits > 0.0) { "模拟课程学分必须是正数" }
    }
}

/** GPA Sandbox 的纯计算结果。targetGap = 目标 GPA - 模拟 GPA。 */
data class GpaSimulationResult(
    val gpa: Double,
    val totalCredits: Double,
    val simulatedCourseCount: Int,
    val targetGap: Double?,
)

/**
 * GPA Sandbox 的第一批领域计算能力。
 *
 * 只处理用户手动输入与显式规则，不读取课程表、成绩单、网络或本地数据库。
 */
object GpaCalculator {
    fun simulate(
        baseline: GpaBaseline,
        courses: List<SimulatedCourse>,
        scale: GradePointScale,
        targetGpa: Double? = null,
    ): GpaSimulationResult {
        require(targetGpa == null || (targetGpa.isFinite() && targetGpa >= 0.0)) {
            "目标 GPA 必须是非负有限数值"
        }

        val simulatedWeightedPoints = courses.sumOf { course ->
            course.credits * scale.pointsFor(course.grade)
        }
        val totalCredits = baseline.credits + courses.sumOf(SimulatedCourse::credits)
        val weightedPoints = baseline.gpa * baseline.credits + simulatedWeightedPoints
        val simulatedGpa = if (totalCredits == 0.0) baseline.gpa else weightedPoints / totalCredits

        return GpaSimulationResult(
            gpa = simulatedGpa,
            totalCredits = totalCredits,
            simulatedCourseCount = courses.size,
            targetGap = targetGpa?.minus(simulatedGpa),
        )
    }
}
