package com.xmu.course.data.auth

/**
 * Wisedu scoped logout 的审计结果与执行边界。
 *
 * Android CookieManager 的公开读取接口不能返回 Domain 属性；在 Domain/origin 未被真实链路确认前，
 * 计划必须保持 unsupported，避免误清其他 WebView 会话。
 */
data class WiseduScopedLogoutPlan(
    val supported: Boolean,
    val cookieUrls: Set<String>,
    val cookieDomains: Set<String>,
    val webStorageOrigins: Set<String>,
    val reason: String,
) {
    companion object {
        fun unsupportedFromAudit(
            observedCookieUrls: Set<String>,
            observedCookieDomains: Set<String>,
            observedWebStorageOrigins: Set<String>,
        ): WiseduScopedLogoutPlan = WiseduScopedLogoutPlan(
            supported = false,
            cookieUrls = observedCookieUrls,
            cookieDomains = observedCookieDomains,
            webStorageOrigins = observedWebStorageOrigins,
            reason = "cookie domain/origin scope 尚未得到足够证据",
        )
    }
}

/** 只提供按 URL、Cookie 名称逐项过期的最小接口；不暴露任何 Cookie 值。 */
interface ScopedWiseduCookieStore {
    fun cookieNames(url: String): List<String>

    fun expire(url: String, name: String)
}

interface ScopedWiseduOriginStore {
    fun deleteOrigin(origin: String)
}

/**
 * 可测试的 scoped cleaner。生产 UI 当前不调用 clear，因为审计计划仍是 unsupported。
 */
class WiseduScopedSessionCleaner(
    private val plan: WiseduScopedLogoutPlan,
    private val cookieStore: ScopedWiseduCookieStore,
    private val originStore: ScopedWiseduOriginStore,
) {
    fun clear(): Boolean {
        if (!plan.supported) return false

        plan.cookieUrls.forEach { url ->
            cookieStore.cookieNames(url).forEach { name -> cookieStore.expire(url, name) }
        }
        plan.webStorageOrigins.forEach(originStore::deleteOrigin)
        return true
    }
}
