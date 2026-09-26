package com.xmu.course.ui.academiccompletion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.CreditsDecimal
import com.xmu.course.data.academiccompletion.SourceCourse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 培养方案页状态机：本地学分确认 + 本机快照删除。
 *
 * 全部行为由用户显式触发：确认才写本地覆盖，点击才删除；
 * 无任何网络访问、无自动同步、无认证操作，也绝不修改来源事实。
 */
class AcademicCompletionViewModel(
    private val store: AcademicCompletionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AcademicCompletionUiState(storeState = store.current()),
    )
    val uiState: StateFlow<AcademicCompletionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.state.collect { state ->
                _uiState.update { it.copy(storeState = state) }
            }
        }
    }

    fun openCreditDialog(course: SourceCourse) {
        _uiState.update {
            it.copy(creditDialogCourse = course, creditInput = "", creditErrorMessage = null)
        }
    }

    fun dismissCreditDialog() {
        _uiState.update {
            it.copy(creditDialogCourse = null, creditInput = "", creditErrorMessage = null)
        }
    }

    fun onCreditInputChange(value: String) {
        _uiState.update { it.copy(creditInput = value, creditErrorMessage = null) }
    }

    /** 学分输入校验：0 < x <= 10 的十进制；合法才写入本地覆盖。取消或校验失败不产生任何写入。 */
    fun submitCreditDialog() {
        val course = _uiState.value.creditDialogCourse ?: return
        val raw = _uiState.value.creditInput
        val normalized = CreditsDecimal.normalizePositive(raw)
        if (normalized == null) {
            _uiState.update { it.copy(creditErrorMessage = "请输入合法的十进制学分，例如 2 或 0.25") }
            return
        }
        if (BigDecimal(normalized) > MaxManualCredits) {
            _uiState.update { it.copy(creditErrorMessage = "单门课程学分不能超过 10") }
            return
        }
        val stored = store.putLocalCreditOverride(course.courseCode, normalized)
        if (!stored) {
            _uiState.update { it.copy(creditErrorMessage = "保存失败，本机数据保持不变") }
            return
        }
        _uiState.update {
            it.copy(
                creditDialogCourse = null,
                creditInput = "",
                creditErrorMessage = null,
                notice = "已记录本地确认学分：${normalized}（不修改来源事实）",
            )
        }
    }

    /** 删除本机全部学业快照数据（快照 + 本地覆盖）；由确认对话框触发。 */
    fun clearLocalData() {
        viewModelScope.launch {
            val ok = kotlinx.coroutines.withContext(ioDispatcher) { store.clearAll() }
            _uiState.update {
                it.copy(notice = if (ok) "已删除本机的学业快照与本地确认" else "删除失败，请稍后重试")
            }
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private companion object {
        val MaxManualCredits = BigDecimal("10")
    }
}
