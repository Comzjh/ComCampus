package com.xmu.course.adapter.jw

import org.json.JSONException
import org.json.JSONObject

/**
 * EMAP 信封解析结果。业务错误消息只保留有限长度，且绝不记录原始响应体。
 */
sealed interface JwEnvelopeResult {
    /** code=="0" 且 datas 存在；payload 为整个信封对象，由调用方按端点语义取数。 */
    data class Ok(val payload: JSONObject) : JwEnvelopeResult

    /** HTTP 层失败（403/504 等）；由上层按端点方言做语义分类。 */
    data class HttpFailure(val status: Int) : JwEnvelopeResult

    /** 网络失败/超时/abort（status==0）。 */
    data object NetworkFailure : JwEnvelopeResult

    /** 信封 code 非 "0"：服务端业务错误。 */
    data class BusinessError(val code: String, val message: String?) : JwEnvelopeResult

    /** 响应不是合法 JSON 信封。 */
    data object Malformed : JwEnvelopeResult

    /** 响应超过大小护栏。 */
    data object TooLarge : JwEnvelopeResult
}

/**
 * EMAP 信封解析：`{code:"0", datas:{<action>:{rows,totalSize}}}`。
 *
 * 单一入口，不做正则清洗；rows/totalSize 的语义解释属于各端点适配器。
 */
object JwEnvelopeParser {

    /** 响应体大小护栏（字符数）：正常最大端点（441 行池 ~90KB）的 40 倍余量。 */
    const val MAX_PAYLOAD_CHARS = 4_000_000

    fun parse(
        status: Int,
        body: String,
        envelope: JwResponseEnvelope = JwResponseEnvelope.EMAP,
    ): JwEnvelopeResult {
        if (status == 0) return JwEnvelopeResult.NetworkFailure
        if (status !in 200..299) return JwEnvelopeResult.HttpFailure(status)
        if (body.length > MAX_PAYLOAD_CHARS) return JwEnvelopeResult.TooLarge
        val root = try {
            JSONObject(body)
        } catch (error: JSONException) {
            return JwEnvelopeResult.Malformed
        }
        if (envelope == JwResponseEnvelope.PLAIN_JSON) {
            // gsapp 原生 JSON：合法 JSON 对象即视为成功载荷，业务语义由调用方解释。
            return JwEnvelopeResult.Ok(root)
        }
        val code = root.optString("code", "")
        if (code == "0" && root.optJSONObject("datas") != null) {
            return JwEnvelopeResult.Ok(root)
        }
        if (code.isNotEmpty() && code != "0") {
            val message = root.optString("msg", "")
                .take(MAX_MESSAGE_CHARS)
                .ifEmpty { null }
            return JwEnvelopeResult.BusinessError(code, message)
        }
        return JwEnvelopeResult.Malformed
    }

    private const val MAX_MESSAGE_CHARS = 120
}
