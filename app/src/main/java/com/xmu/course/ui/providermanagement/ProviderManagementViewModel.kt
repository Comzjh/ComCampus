package com.xmu.course.ui.providermanagement

import androidx.lifecycle.ViewModel
import com.xmu.course.contracts.provider.ProviderManagementEntry

/**
 * 数据来源与管理 ViewModel：只读展示组合根聚合的 Provider 清单。
 *
 * 本轮无任何写操作：不清理数据、不触发同步、不访问网络；
 * 未来 PrivacyDataOwner 接入后再扩展。
 */
class ProviderManagementViewModel(
    val entries: List<ProviderManagementEntry>,
)