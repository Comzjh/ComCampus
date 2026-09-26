package com.xmu.course.contracts.provider

/**
 * Provider 管理页的聚合条目：由组合根装配。
 *
 * - [descriptor] 是稳定机器身份描述（静态），禁止增加 displayName/icon/status 字段；
 * - [statusSource] 是可选的运行时状态源（动态），由 Adapter 侧提供、UI 只读；
 *   未提供时不展示状态行；
 * - [dataOwner] 是可选的隐私数据所有者（Phase 5.3.2-C1 只读计数），
 *   仅声明数据清理边界，UI 本轮不展示清理入口。
 */
data class ProviderManagementEntry(
    val descriptor: ProviderDescriptor,
    val statusSource: AuthStateSource? = null,
    val dataOwner: PrivacyDataOwner? = null,
)