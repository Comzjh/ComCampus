package com.xmu.course.data.tronclass.auth

/**
 * 从 WebView 的 Cookie 头中提取一个已验证的会话 Cookie。
 *
 * [verifiedCookieName] 必须来自已确认的 TronClass API 认证契约；为空时 fail-closed，
 * 不会把任意 Cookie 当成登录成功，也不会保存完整 Cookie 头。
 */
class TronSessionExtractor(
    private val verifiedCookieName: String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun extract(cookieHeader: String?): TronSession? {
        val cookieName = verifiedCookieName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val header = cookieHeader ?: return null
        val pair = header.split(';')
            .asSequence()
            .map { it.trim() }
            .mapNotNull { segment ->
                val separator = segment.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                segment.substring(0, separator).trim() to segment.substring(separator + 1).trim()
            }
            .firstOrNull { (name, value) -> name == cookieName && value.isNotEmpty() }
            ?: return null

        val now = clock()
        return TronSession(
            sessionCookieName = pair.first,
            sessionId = pair.second,
            createdAt = now,
            updatedAt = now,
        )
    }
}
