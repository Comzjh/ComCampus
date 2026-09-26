package com.xmu.course.contracts.provider

/**
 * Provider 的稳定机器标识；未来各校园数据 Provider（如 xmu.wisedu、xmu.tronclass、xmu.jw）
 * 以此自我声明。
 *
 * 约束：id 必须稳定且不含个人信息；不包含任何学校系统 URL 或实现细节。
 * 本轮仅定义契约，不要求任何现有类实现。
 */
interface ProviderIdentity {
    val id: String
}