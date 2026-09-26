package com.xmu.course.data.auth

/**
 * JW 页面产生的匿名观察结果。
 *
 * 只允许保留 host/path 和可选的非敏感机器信号；不携带 Cookie、Token、Session ID
 * 或页面正文。当前未取得真实证据时，所有 signal 默认均为 false。
 */
data class WiseduAuthObservation(
    val host: String? = null,
    val path: String? = null,
    val pageTitle: String? = null,
    val httpStatus: Int? = null,
    val authenticatedSignal: Boolean = false,
    val authRequiredSignal: Boolean = false,
    val expiredSignal: Boolean = false,
)

fun interface WiseduAuthVerifier {
    fun verify(observation: WiseduAuthObservation): AuthStatus
}

data class WiseduApiResponseObservation(
    val method: String,
    val host: String?,
    val path: String?,
    val httpStatus: Int?,
    val contentType: String?,
    val redirected: Boolean = false,
    val networkError: Boolean = false,
)

/** 基于真实 JW endpoint 对照得到的机器信号；不读取响应体或个人字段。 */
object WiseduAuthenticatedApiSignal {
    const val HOST = "jw.xmu.edu.cn"
    const val PATH = "/jwapp/sys/jwai/api/user/getCurrentUser.do"

    fun verify(response: WiseduApiResponseObservation): AuthStatus {
        if (response.networkError ||
            !response.method.equals("GET", ignoreCase = true) ||
            response.host != HOST ||
            response.path != PATH ||
            response.redirected
        ) {
            return AuthStatus.UNKNOWN
        }
        return when {
            response.httpStatus == 200 && response.contentType.isJsonContentType() ->
                AuthStatus.AUTHENTICATED
            response.httpStatus == 200 && response.contentType.isHtmlContentType() ->
                AuthStatus.AUTH_REQUIRED
            else -> AuthStatus.UNKNOWN
        }
    }
}

private fun String?.isJsonContentType(): Boolean =
    this?.substringBefore(';')?.trim()?.equals("application/json", ignoreCase = true) == true

private fun String?.isHtmlContentType(): Boolean =
    this?.substringBefore(';')?.trim()?.equals("text/html", ignoreCase = true) == true

/** 未经真实链路确认时的 fail-closed 验证策略。 */
object ConservativeWiseduAuthVerifier : WiseduAuthVerifier {
    override fun verify(observation: WiseduAuthObservation): AuthStatus = when {
        observation.authenticatedSignal -> AuthStatus.AUTHENTICATED
        observation.expiredSignal -> AuthStatus.EXPIRED
        observation.authRequiredSignal -> AuthStatus.AUTH_REQUIRED
        else -> AuthStatus.UNKNOWN
    }
}
