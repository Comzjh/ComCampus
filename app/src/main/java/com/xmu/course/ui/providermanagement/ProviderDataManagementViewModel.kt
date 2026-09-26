package com.xmu.course.ui.providermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.contracts.provider.ProviderManagementEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 单个 Provider 的数据管理 ViewModel（Phase 5.3.2-C3：count → confirm → delete → refresh）。
 *
 * - countLocalData()/clearLocalData() 仅由用户在 UI 主动触发；
 * - 只操作本 Provider 的 DataOwner，无跨 Provider 删除；
 * - 无网络访问、无同步、无认证操作。
 */
class ProviderDataManagementViewModel(
    val providerId: String,
    private val owner: PrivacyDataOwner?,
) : ViewModel() {

    constructor(entry: ProviderManagementEntry) : this(entry.descriptor.id, entry.dataOwner)

    /** 数据管理页状态：Loading → Loaded(count)；无 dataOwner 或读取失败 → Unavailable。 */
    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val count: Int) : UiState
        data object Unavailable : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    /** 删除结果提示；非 null 时展示一次并由 [consumeResultMessage] 清除。 */
    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    /** 计数无法读取时，只要存在属主仍允许用户确认清除损坏数据。 */
    val canClearLocalData: Boolean
        get() = owner != null



    init {
        refreshCount()
    }

    fun refreshCount() {
        val dataOwner = owner
        if (dataOwner == null) {
            _state.value = UiState.Unavailable
            return
        }
        viewModelScope.launch {
            _state.value = loadCount(dataOwner)
        }
    }

    private suspend fun loadCount(dataOwner: PrivacyDataOwner): UiState = try {
        UiState.Loaded(dataOwner.countLocalData())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        UiState.Unavailable
    }

    /** 用户在确认 Dialog 点击确认后调用；取消路径不会进入本方法。 */
    fun deleteLocalData() {
        val dataOwner = owner ?: return
        if (_deleting.value) return
        _resultMessage.value = null
        _deleting.value = true
        viewModelScope.launch {
            try {
                try {
                    dataOwner.clearLocalData()
                    _resultMessage.value = "已删除该来源的本机数据"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _resultMessage.value = "删除失败，请稍后重试"
                }
                _state.value = loadCount(dataOwner)
            } finally {
                _deleting.value = false
            }
        }
    }

    fun consumeResultMessage() {
        _resultMessage.value = null
    }
}

/** Creates a source-keyed ViewModel that the app root can retain across route changes. */
class ProviderDataManagementViewModelFactory(
    private val sourceId: String,
    private val owner: PrivacyDataOwner?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProviderDataManagementViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return ProviderDataManagementViewModel(sourceId, owner) as T
    }
}
