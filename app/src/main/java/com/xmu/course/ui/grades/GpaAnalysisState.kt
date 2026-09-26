package com.xmu.course.ui.grades

/** GPA 分析的只读界面状态；数据全部来自纯计算，不涉及持久化。 */
sealed interface GpaAnalysisState {
    data object Loading : GpaAnalysisState
    /** TranscriptReader 当前不可用（无快照）。 */
    data object Unavailable : GpaAnalysisState
    /** 快照存在但没有任何课程。 */
    data object NoCourses : GpaAnalysisState
    /** 有课程但没有一门可换算为绩点。 */
    data object Insufficient : GpaAnalysisState
    /** 计算成功。 */
    data class Ready(val analysis: TranscriptGpaAnalysis.Computed) : GpaAnalysisState
}
