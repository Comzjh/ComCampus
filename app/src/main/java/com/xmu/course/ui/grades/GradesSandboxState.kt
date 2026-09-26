package com.xmu.course.ui.grades

import com.xmu.course.domain.grades.GpaSimulationResult
import com.xmu.course.ui.academic.AcademicSimulationSeed

/**
 * GPA Sandbox（学业模拟）的会话内状态。
 *
 * 基线来自已同步教务数据（seed），不再是用户必填项；
 * 手动基线是显式开启的例外通道，且只影响本次模拟。
 */

/** 模拟课程行的稳定来源前缀：本学期自动注入 vs 用户自定义未来课程。 */
const val SEEDED_COURSE_PREFIX = "seed:"
const val CUSTOM_COURSE_PREFIX = "custom:"

/** GPA Sandbox 中的一门模拟课程（session-only，不落库）。 */
data class SimulatedCourseInputState(
    val id: Long,
    /** 稳定标识：seed:课程号 或 custom:本地 id；刷新时用于保留用户已有编辑。 */
    val key: String,
    val name: String = "",
    val credits: String = "",
    val grade: String = "",
    val score: Int = DEFAULT_SCORE,
    /** false = 用户尚未设置模拟成绩 → 不参与计算，绝不代猜。 */
    val scoreSet: Boolean = false,
    val seeded: Boolean = false,
    val outsidePlan: Boolean = false,
) {
    /** 该课程是否参与本次模拟：显式设置过成绩才算。 */
    val participates: Boolean
        get() = scoreSet || grade.isNotBlank()

    companion object {
        const val DEFAULT_SCORE = 60
    }
}

/** 学业模拟页状态。 */
data class GradesSandboxState(
    /** 自动载入的教务基线（只读事实）。 */
    val seed: AcademicSimulationSeed = AcademicSimulationSeed.EMPTY,
    /** 本机缓存读取失败：需要恢复动作，不当作「没有数据」。 */
    val storageUnavailable: Boolean = false,
    /** 手动基线开关：默认关闭，开启后仅影响本次模拟。 */
    val manualBaselineEnabled: Boolean = false,
    val manualCurrentGpa: String = "",
    val manualCompletedCredits: String = "",
    val targetGpa: String = "",
    val courses: List<SimulatedCourseInputState> = emptyList(),
    val result: GpaSimulationResult? = null,
    val errorMessage: String? = null,
) {
    /** 实际参与计算的课程数。 */
    val participatingCount: Int
        get() = courses.count { it.participates }

    /** 已注入但尚未设置成绩的课程数（结果卡需明示「未参与」）。 */
    val skippedCount: Int
        get() = courses.count { !it.participates }

    /** 学分待确认、因此无法进入模拟的课程。 */
    val unresolvedCount: Int
        get() = seed.unresolvedCourses.size

    /** 已有真实成绩、已计入基线的本学期课程。 */
    val gradedCount: Int
        get() = seed.alreadyGradedCourses.size

    /** 可以开始模拟：教务基线可用，或手动基线已填。 */
    val canSimulate: Boolean
        get() = if (manualBaselineEnabled) {
            manualCurrentGpa.isNotBlank() && manualCompletedCredits.isNotBlank()
        } else {
            seed.canSimulate
        }
}
