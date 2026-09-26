package com.xmu.course.ui.academic

/**
 * 学业模拟的数据可用性（BUG-05 唯一判据）。
 *
 * 「有学业数据」「有可用 GPA」「有学分」「可以模拟」是四件事，过去分别由
 * `sourceAvailable`、`usableGpaText`、`earnedCreditsText` 三处各自解释，
 * 才会出现"学业页有数据、模拟页却说没数据"。现在 seed / ViewModel / 页面共用这一处。
 */
sealed class AcademicSimulationAvailability {

    /** 学分进度与可用 GPA 齐备：可以直接从本机基线开始模拟。 */
    data class Ready(val seed: AcademicSimulationSeed) : AcademicSimulationAvailability()

    /** 只有一半：必须说清楚缺的是哪一半，不能笼统说"没有数据"。 */
    data class Partial(
        val creditsAvailable: Boolean,
        val gradesAvailable: Boolean,
    ) : AcademicSimulationAvailability()

    /** 本机确实还没有任何学业快照。 */
    object Empty : AcademicSimulationAvailability()

    val canSimulate: Boolean
        get() = this is Ready

    /**
     * 面向用户的说明：只描述真实缺口。
     *
     * 禁止出现"未导入""Excel""PDF"——学业数据只有"刷新"这一条真实来源。
     */
    val guidance: String?
        get() = when (this) {
            is Ready -> null
            is Empty -> "尚未同步学业数据。"
            is Partial -> when {
                creditsAvailable && !gradesAvailable -> "已有学分进度，暂无可用于 GPA 计算的成绩。"
                gradesAvailable && !creditsAvailable -> "已有成绩数据，仍缺少计算所需的学分信息。"
                else -> "学业数据还不完整：学分进度与可用 GPA 都还没同步到。"
            }
        }
}
