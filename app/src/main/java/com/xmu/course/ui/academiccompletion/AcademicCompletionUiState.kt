package com.xmu.course.ui.academiccompletion

import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.SourceCourse

/**
 * 培养方案页状态：store 状态 + 学分确认对话框输入 + 一次性提示。
 *
 * 说明：提示与对话框输入都是展示层状态；来源事实与本地覆盖的边界
 * 由数据模型保证，本层只做呈现。快照唯一生产通道是官方手动刷新。
 */
data class AcademicCompletionUiState(
    val storeState: AcademicCompletionStore.State = AcademicCompletionStore.State.Empty,
    val creditDialogCourse: SourceCourse? = null,
    val creditInput: String = "",
    val creditErrorMessage: String? = null,
    val notice: String? = null,
)
