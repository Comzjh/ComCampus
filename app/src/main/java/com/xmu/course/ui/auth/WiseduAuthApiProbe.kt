package com.xmu.course.ui.auth

import android.webkit.WebView
import com.xmu.course.data.auth.WiseduApiResponseObservation
import org.json.JSONArray
import org.json.JSONObject

/**
 * 使用当前 JW WebView 的 credentials 执行只读认证接口探测。
 * 只回传 status/content-type/最终 host/path/redirected，不读取 response body。
 */
internal object WiseduAuthApiProbe {
    private const val RESULT_KEY = "__xmuAuthApiProbeResult"

    fun probe(webView: WebView, onResult: (WiseduApiResponseObservation) -> Unit) {
        val target = "https://${com.xmu.course.data.auth.WiseduAuthenticatedApiSignal.HOST}" +
            com.xmu.course.data.auth.WiseduAuthenticatedApiSignal.PATH
        val script = """
            (function() {
                window.$RESULT_KEY = null;
                fetch(${JSONObject.quote(target)}, {credentials: 'include', redirect: 'manual'})
                    .then(function(r) {
                        window.$RESULT_KEY = JSON.stringify([r.status, r.headers.get('content-type') || '', r.url, r.redirected]);
                    })
                    .catch(function() { window.$RESULT_KEY = JSON.stringify(['error']); });
                return true;
            })()
        """.trimIndent()
        webView.evaluateJavascript(script, null)

        fun readResult(attempt: Int) {
            webView.evaluateJavascript("window.$RESULT_KEY") { raw ->
                val encoded = runCatching { JSONObject("{\"value\":$raw}").get("value") }.getOrNull()
                val result = when (encoded) {
                    is String -> runCatching { JSONArray(encoded) }.getOrNull()
                    else -> null
                }
                if (result == null) {
                    if (attempt < 3) {
                        webView.postDelayed({ readResult(attempt + 1) }, 250)
                    } else {
                        onResult(networkError())
                    }
                    return@evaluateJavascript
                }
                if (result.optString(0) == "error") {
                    onResult(networkError())
                    return@evaluateJavascript
                }
                val uri = runCatching { java.net.URI(result.optString(2)) }.getOrNull()
                onResult(
                    WiseduApiResponseObservation(
                        method = "GET",
                        host = uri?.host,
                        path = uri?.path,
                        httpStatus = result.optInt(0, -1).takeIf { it >= 0 },
                        contentType = result.optString(1).takeIf { it.isNotBlank() },
                        redirected = result.optBoolean(3),
                    ),
                )
            }
        }
        webView.postDelayed({ readResult(0) }, 350)
    }

    private fun networkError() = WiseduApiResponseObservation(
        method = "GET",
        host = null,
        path = null,
        httpStatus = null,
        contentType = null,
        networkError = true,
    )
}
