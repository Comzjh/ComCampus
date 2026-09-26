package com.xmu.course.adapter.jw

import org.json.JSONObject

/**
 * 官方页面上下文内 same-origin 只读请求的 JS 构造器。
 *
 * 安全设计：
 * - 不向页面暴露任何 Android native 对象（无 addJavascriptInterface）；
 *   结果写入私有 window 槽位，由 Kotlin 轮询读回；
 * - 只发起 [JwEndpointPolicy] 已登记的 same-origin 相对路径请求；
 * - 不读取 document.cookie，不打印响应内容；
 * - credentials:'include' 允许浏览器自行携带会话（凭据始终留在 WebView），
 *   与集成包 README §3 的统一注入模式一致；
 * - AbortController 提供 20s 硬超时，禁止同步 XHR。
 */
object JwFetchScriptBuilder {

    /** 私有结果槽位命名空间；不与页面自身变量冲突。 */
    const val RESULT_NAMESPACE = "__ccJwRead"

    /** 单请求超时（毫秒），集成包建议值。 */
    const val FETCH_TIMEOUT_MS = 20_000

    /** 探测当前页面 origin（用于可信域校验）。 */
    fun originProbeScript(): String = "(function(){ return { origin: location.origin }; })()"

    /**
     * xywccx 注入前置条件探测：官方 SPA 上下文与 BH_UTILS 均就绪。
     * cjcx/zmsqxmu 仅要求 origin 可信，不依赖 BH_UTILS。
     */
    fun readinessProbeScript(): String =
        "(function(){ return { ready: !!(window._JW_INIT_CONFIG && window.BH_UTILS) }; })()"

    /**
     * 构造启动脚本：异步 fetch 一个已登记端点，结果写入
     * `window.__ccJwRead[requestId]`（pending → done/error）。
     *
     * @param queryForGet GET 端点的已编码查询串（不含前导 `?`）；POST 时传空串并携带 body。
     * @throws IllegalArgumentException 端点不在只读白名单（含写端点拒绝清单）时。
     */
    fun startFetchScript(
        endpoint: JwReadEndpoint,
        requestId: String,
        formBody: String,
        queryForGet: String = "",
    ): String {
        require(JwEndpointPolicy.isReadAllowed(endpoint.path)) {
            "endpoint not allowed for read: ${endpoint.path}"
        }
        require(REQUEST_ID_PATTERN.matches(requestId)) { "invalid request id" }
        if (endpoint.signed) {
            // Phase H 实测：xywccx 家族无签名 fetch 一律 403；必须走页面内 BH_UTILS.doSyncAjax 签名通道。
            val paramSource =
                if (endpoint.method == JwHttpMethod.GET) queryForGet else formBody
            return signedFetchScript(endpoint, requestId, paramSource)
        }
        val urlLiteral = when {
            endpoint.method == JwHttpMethod.GET && queryForGet.isNotEmpty() ->
                JSONObject.quote("${endpoint.path}?$queryForGet")
            else -> JSONObject.quote(endpoint.path)
        }
        val methodLiteral = JSONObject.quote(endpoint.method.name)
        val bodyLiteral = JSONObject.quote(formBody)
        val slotLiteral = JSONObject.quote(requestId)
        return """
            (function () {
              var ns = window['$RESULT_NAMESPACE'] = window['$RESULT_NAMESPACE'] || {};
              var slot = $slotLiteral;
              // 学号占位符只在官方页面上下文内替换；Kotlin 层不接触任何身份信息。
              var xh = encodeURIComponent((window._JW_INIT_CONFIG && window._JW_INIT_CONFIG.userid) || '');
              ns[slot] = { state: 'pending' };
              var ctrl = new AbortController();
              var timer = setTimeout(function () { ctrl.abort(); }, $FETCH_TIMEOUT_MS);
              var url = $urlLiteral.replace(/\{\{XH\}\}/g, xh);
              var options = {
                method: $methodLiteral,
                credentials: 'include',
                signal: ctrl.signal,
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                  'X-Requested-With': 'XMLHttpRequest'
                }
              };
              if (options.method === 'POST') {
                options.body = $bodyLiteral.replace(/\{\{XH\}\}/g, xh);
              }
              fetch(url, options)
                  .then(function (r) {
                    return r.text().then(function (t) { return { status: r.status, body: t }; });
                  })
                .then(function (res) {
                  clearTimeout(timer);
                  ns[slot] = { state: 'done', status: res.status, body: res.body };
                })
                .catch(function () {
                  clearTimeout(timer);
                  ns[slot] = { state: 'error', status: 0, body: '' };
                });
              return { started: true };
            })()
        """.trimIndent()
    }

    /**
     * Phase H 实测签名通道：xywccx 家族端点必须经页面内 `BH_UTILS.doSyncAjax`。
     *
     * 实测事实（2026-09-20 真机）：
     * - 裸 fetch 与裸 $.ajax 均 403：jw_security+goldenfinger 签名配置在 doSyncAjax 内部；
     * - doSyncAjax(url, params) 以 async:false 同步返回已解析 JSON（成功含 code/datas 或原生对象）；
     * - 空对象/异常 = 请求失败：置 error 槽 → NetworkFailure，旧缓存保持不变。
     */
    private fun signedFetchScript(
        endpoint: JwReadEndpoint,
        requestId: String,
        paramSource: String,
    ): String {
        val urlLiteral = JSONObject.quote(endpoint.path)
        val paramsLiteral = JSONObject.quote(paramSource)
        val slotLiteral = JSONObject.quote(requestId)
        val requireCode = if (endpoint.envelope == JwResponseEnvelope.EMAP) "true" else "false"
        return """
            (function () {
              var ns = window['$RESULT_NAMESPACE'] = window['$RESULT_NAMESPACE'] || {};
              var slot = $slotLiteral;
              ns[slot] = { state: 'pending' };
              try {
                var xh = encodeURIComponent((window._JW_INIT_CONFIG && window._JW_INIT_CONFIG.userid) || '');
                // 学号占位符只在官方页面上下文内替换；Kotlin 层不接触任何身份信息。
                var raw = $paramsLiteral.replace(/\{\{XH\}\}/g, xh);
                var params = {};
                if (raw) {
                  raw.split('&').forEach(function (kv) {
                    var i = kv.indexOf('=');
                    if (i < 0) { return; }
                    params[decodeURIComponent(kv.slice(0, i).replace(/\+/g, ' '))] =
                        decodeURIComponent(kv.slice(i + 1).replace(/\+/g, ' '));
                  });
                }
                var resp = window.BH_UTILS.doSyncAjax($urlLiteral, params);
                var ok = !!resp && typeof resp === 'object' && Object.keys(resp).length > 0;
                if (ok && $requireCode) { ok = ('code' in resp); }
                if (ok) {
                  ns[slot] = { state: 'done', status: 200, body: JSON.stringify(resp) };
                } else {
                  ns[slot] = { state: 'error', status: 0, body: '' };
                }
              } catch (e) {
                ns[slot] = { state: 'error', status: 0, body: '' };
              }
              return { started: true };
            })()
        """.trimIndent()
    }

    /** 读取槽位当前状态；返回值即 evaluateJavascript 的 JSON 结果。 */
    fun readResultScript(requestId: String): String {
        require(REQUEST_ID_PATTERN.matches(requestId)) { "invalid request id" }
        val slotLiteral = JSONObject.quote(requestId)
        return "(function(){ var ns = window['$RESULT_NAMESPACE'] || {};" +
            " return ns[$slotLiteral] || { state: 'missing' }; })()"
    }

    /** 清理槽位，避免结果在页面内存中滞留。 */
    fun clearResultScript(requestId: String): String {
        require(REQUEST_ID_PATTERN.matches(requestId)) { "invalid request id" }
        val slotLiteral = JSONObject.quote(requestId)
        return "(function(){ if (window['$RESULT_NAMESPACE']) {" +
            " delete window['$RESULT_NAMESPACE'][$slotLiteral]; } return { cleared: true }; })()"
    }

    private val REQUEST_ID_PATTERN = Regex("^[a-z0-9_]{1,40}$")
}
