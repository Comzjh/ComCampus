package com.xmu.course.ui.campusservice

import androidx.lifecycle.ViewModel
import com.xmu.course.contracts.campusservice.CampusServiceDescriptor
import com.xmu.course.contracts.campusservice.CampusServiceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 校园服务中心 ViewModel：只依赖 [CampusServiceProvider] 契约。
 *
 * - 服务清单来自 provider.services()，UI 不感知 JW/URL/WebView/Adapter；
 * - openService 仅由用户点击触发（MANUAL_ONLY），不自动访问、不后台同步；
 * - 未实现服务由 provider 明确拒绝，这里转为可展示的错误状态。
 */
class CampusServiceViewModel(
    private val provider: CampusServiceProvider,
) : ViewModel() {
    val services: List<CampusServiceDescriptor> = provider.services()

    private val _unsupportedServiceId = MutableStateFlow<String?>(null)
    val unsupportedServiceId: StateFlow<String?> = _unsupportedServiceId.asStateFlow()

    fun openService(serviceId: String) {
        try {
            provider.openService(serviceId)
        } catch (_: IllegalArgumentException) {
            _unsupportedServiceId.value = serviceId
        }
    }

    fun consumeUnsupportedService() {
        _unsupportedServiceId.value = null
    }
}