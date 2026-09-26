package com.xmu.course.data.tronclass.auth

import android.content.Context

/** TronSessionStore 的 Android Keystore + EncryptedSharedPreferences 实现。 */
class EncryptedTronSessionStore internal constructor(
    private val storage: TronSecureStorage,
) : TronSessionStore {
    constructor(context: Context) : this(EncryptedTronSecureStorage(context.applicationContext))

    override fun saveSession(session: TronSession) {
        // 单次提交整个最小对象，避免多字段写入中途失败留下半个会话。
        storage.putString(PAYLOAD_KEY, TronSessionCodec.encode(session))
    }

    override fun getSession(): TronSession? = runCatching {
        val payload = storage.getString(PAYLOAD_KEY) ?: return null
        TronSessionCodec.decode(payload)
    }.getOrNull()

    override fun clearSession() {
        storage.removeAll()
    }

    private companion object {
        const val PAYLOAD_KEY = "session_payload"
    }
}
