package com.xmu.course.contracts.campusservice

import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor

/**
 * 校园服务 Provider 契约：Feature 只依赖此契约获取服务清单并打开服务，
 * 不感知具体学校系统、URL、WebView 或认证细节。
 *
 * MANUAL_ONLY：openService 必须由用户主动触发；Provider 禁止自动打开页面、
 * 后台访问或保存任何凭据。
 *
 * 单入口兼容：既有 [CampusServiceLauncher] 继续保留，作为"打开单个服务"的
 * 兼容动作；多服务清单与按 id 打开统一走本契约。
 */
interface CampusServiceProvider {
    /** Provider 级自描述：稳定 id、能力与同步策略。 */
    fun descriptor(): ProviderDescriptor

    /** 当前已实现的服务清单；未实现的服务不得出现在清单中。 */
    fun services(): List<CampusServiceDescriptor>

    /**
     * 用户主动打开指定服务；未实现或未知 serviceId 必须明确失败，
     * 不得静默降级到其他服务。
     */
    fun openService(serviceId: String)
}

/**
 * 单个校园服务的稳定描述。
 *
 * @property serviceId 稳定机器标识（如 `xmu.jw.academic_completion`）；
 * 不含 URL、不含用户信息、不绑定 WebView 或任何实现细节。
 * @property capability 该服务归属的业务能力域。
 * @property displayKey 稳定的 UI 文案键；文案由 Feature 层映射，Adapter 不返回展示字符串。
 */
data class CampusServiceDescriptor(
    val serviceId: String,
    val capability: ProviderCapability,
    val displayKey: String,
) {
    init {
        require(serviceId.isNotBlank()) { "serviceId must not be blank" }
        require(displayKey.isNotBlank()) { "displayKey must not be blank" }
    }
}