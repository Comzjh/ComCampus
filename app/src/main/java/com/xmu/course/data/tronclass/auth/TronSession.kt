package com.xmu.course.data.tronclass.auth

/**
 * TronClass API 所需的最小会话信息。
 *
 * 只保存一个经过验证的会话 Cookie 名称和值，不保存完整 Cookie 串、用户名、密码、HTML
 * 或 WebView 页面数据。会话值始终由 [TronSessionStore] 加密保存。
 */
data class TronSession(
    val sessionCookieName: String,
    val sessionId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
) {
    init {
        require(sessionCookieName.isNotBlank()) { "session cookie name must not be blank" }
        require(sessionId.isNotBlank()) { "session id must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt > 0L) { "updatedAt must be positive" }
        require(expiresAt == null || expiresAt > 0L) { "expiresAt must be positive" }
    }

    fun isExpired(now: Long): Boolean = expiresAt?.let { now >= it } == true
}
