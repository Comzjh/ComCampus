package com.xmu.course.adapter.jw

import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** 页面可信状态探测结果。 */
enum class JwPageCheck {
    /** 官方域且注入前置条件就绪。 */
    TRUSTED_READY,

    /** 官方域，但 xywccx SPA 上下文未就绪（可能仍在 CAS 登录/加载中）。 */
    TRUSTED_NOT_READY,

    /** 当前页面不在可信官方域（如停留在 CAS 登录页）→ 需要用户手动登录。 */
    UNTRUSTED_ORIGIN,

    /** 探测脚本无法执行或返回异常。 */
    PROBE_FAILURE,
}

/**
 * 共享安全 WebView 只读请求桥。
 *
 * 职责：
 * - 只执行 [JwEndpointPolicy] 白名单内的 same-origin 只读请求；
 * - 执行前校验页面 origin（及 xywccx 场景的 SPA 就绪条件）；
 * - 异步 fetch + 槽位轮询（禁同步 XHR），带整体超时与生命周期取消；
 * - 结果只以类型化 [JwEnvelopeResult] 交给调用方；不记录原始响应体。
 *
 * 不持有 WebView 引用：JS 求值通过注入的 [evaluate] 完成，
 * 便于独立单元测试与生命周期绑定。
 */
class JwWebViewReadExecutor(
    private val evaluate: suspend (script: String) -> String?,
    private val await: suspend (delayMs: Long) -> Unit,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val overallTimeoutMs: Long = DEFAULT_OVERALL_TIMEOUT_MS,
) {
    private val sequence = AtomicLong(0)

    /** 校验当前页面 origin 可信；[requireBhUtils] 时同时要求 xywccx SPA 就绪。 */
    suspend fun verifyTrustedPage(requireBhUtils: Boolean): JwPageCheck {
        val originRaw = evaluate(JwFetchScriptBuilder.originProbeScript()) ?: return JwPageCheck.PROBE_FAILURE
        val origin = runCatching { JSONObject(originRaw).optString("origin") }.getOrNull()
        if (!JwEndpointPolicy.isTrustedOrigin(origin)) return JwPageCheck.UNTRUSTED_ORIGIN
        if (!requireBhUtils) return JwPageCheck.TRUSTED_READY
        val readyRaw = evaluate(JwFetchScriptBuilder.readinessProbeScript()) ?: return JwPageCheck.PROBE_FAILURE
        val ready = runCatching { JSONObject(readyRaw).optBoolean("ready") }.getOrDefault(false)
        return if (ready) JwPageCheck.TRUSTED_READY else JwPageCheck.TRUSTED_NOT_READY
    }

    /**
     * 在可信官方域内有界等待 xywccx SPA 就绪。
     *
     * 真机实测（2026-09-23）：官方 xywccx SPA 的 onPageFinished 比 window.BH_UTILS 定义
     * 早约 4 秒（页面自身脚本此刻仍在抛 "BH_UTILS is not defined"），
     * 落点即刻注入必然读到未就绪，导致 xywccx 源在任何一次取数前就中止。
     *
     * 因此只对 [JwPageCheck.TRUSTED_NOT_READY] 重试；origin 不可信或探测失败立即返回，
     * 避免在 CAS 登录页上空等。超时后仍返回 [JwPageCheck.TRUSTED_NOT_READY]，
     * 语义与单次探测一致：fail-closed，绝不触碰本地缓存。
     */
    suspend fun awaitTrustedPage(
        requireBhUtils: Boolean,
        timeoutMs: Long = DEFAULT_READINESS_TIMEOUT_MS,
    ): JwPageCheck {
        val maxAttempts = (timeoutMs / pollIntervalMs).coerceAtLeast(1L)
        var attempts = 0L
        while (true) {
            coroutineContext.ensureActive()
            val check = verifyTrustedPage(requireBhUtils)
            if (check != JwPageCheck.TRUSTED_NOT_READY) return check
            attempts += 1
            if (attempts >= maxAttempts) return check
            await(pollIntervalMs)
        }
    }

    /**
     * 发起一次白名单只读请求并等待结果。
     *
     * @param formBody POST 表单体（各端点适配器按自己的方言构造，禁止跨方言复用）。
     * @param queryForGet GET 端点的已编码查询串（不含前导 `?`）。
     * @throws IllegalArgumentException 端点不在只读白名单（包括任何写端点）。
     */
    suspend fun fetch(
        endpoint: JwReadEndpoint,
        formBody: String = "",
        queryForGet: String = "",
    ): JwEnvelopeResult {
        require(JwEndpointPolicy.isReadAllowed(endpoint.path)) {
            "endpoint not allowed for read: ${endpoint.path}"
        }
        val requestId = "r${sequence.incrementAndGet()}"
        val startedRaw = evaluate(
            JwFetchScriptBuilder.startFetchScript(endpoint, requestId, formBody, queryForGet),
        ) ?: return JwEnvelopeResult.NetworkFailure
        val started = runCatching { JSONObject(startedRaw).optBoolean("started") }.getOrDefault(false)
        if (!started) return JwEnvelopeResult.Malformed

        val deadline = System.currentTimeMillis() + overallTimeoutMs
        try {
            while (true) {
                coroutineContext.ensureActive()
                val slotRaw = evaluate(JwFetchScriptBuilder.readResultScript(requestId))
                    ?: return JwEnvelopeResult.NetworkFailure
                val slot = runCatching { JSONObject(slotRaw) }.getOrNull()
                    ?: return JwEnvelopeResult.Malformed
                when (slot.optString("state")) {
                    "done" -> {
                        return JwEnvelopeParser.parse(
                            status = slot.optInt("status", 0),
                            body = slot.optString("body"),
                            envelope = endpoint.envelope,
                        )
                    }
                    "error" -> return JwEnvelopeResult.NetworkFailure
                    "missing" -> return JwEnvelopeResult.Malformed
                    "pending" -> {
                        if (System.currentTimeMillis() > deadline) return JwEnvelopeResult.NetworkFailure
                        await(pollIntervalMs)
                    }
                    else -> return JwEnvelopeResult.Malformed
                }
            }
        } finally {
            // 槽位清理是尽力而为：失败不影响结果，也不吞掉取消。
            runCatching { evaluate(JwFetchScriptBuilder.clearResultScript(requestId)) }
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 400L

        /** fetch 自身有 20s abort；整体超时留出信封解析与调度余量。 */
        const val DEFAULT_OVERALL_TIMEOUT_MS = 30_000L

        /** xywccx SPA 冷启动就绪等待上限（真机实测官方页面 JS 约需 4 秒，留足弱网余量）。 */
        const val DEFAULT_READINESS_TIMEOUT_MS = 20_000L
    }
}
