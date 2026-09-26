package com.xmu.course.contracts.provider

/**
 * Provider 自描述：把 identity、能力与同步策略组合为一个值对象。
 * 仅由本包契约组成，不引入注册机制；未来 Adapter 以此声明自身。
 */
data class ProviderDescriptor(
    val id: String,
    val capabilities: Set<ProviderCapability>,
    val syncPolicy: SyncPolicy,
) {
    init {
        require(id.isNotBlank()) { "provider id must not be blank" }
    }
}