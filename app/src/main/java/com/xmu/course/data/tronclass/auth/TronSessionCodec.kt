package com.xmu.course.data.tronclass.auth

import java.nio.charset.StandardCharsets
import java.util.Base64

/** 纯 JVM 可测试的最小会话载荷编码器；编码结果仍由安全存储整体加密。 */
internal object TronSessionCodec {
    private const val FIELD_COUNT = 5

    fun encode(session: TronSession): String = listOf(
        session.sessionCookieName,
        session.sessionId,
        session.createdAt.toString(),
        session.updatedAt.toString(),
        session.expiresAt?.toString().orEmpty(),
    ).joinToString(SEPARATOR) { value ->
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(payload: String): TronSession? = runCatching {
        val fields = payload.split(SEPARATOR)
        if (fields.size != FIELD_COUNT) return null
        val decoded = fields.map { encoded ->
            String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        }
        TronSession(
            sessionCookieName = decoded[0],
            sessionId = decoded[1],
            createdAt = decoded[2].toLong(),
            updatedAt = decoded[3].toLong(),
            expiresAt = decoded[4].takeIf { it.isNotEmpty() }?.toLong(),
        )
    }.getOrNull()

    private const val SEPARATOR = "."
}
