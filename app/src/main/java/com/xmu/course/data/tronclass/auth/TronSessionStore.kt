package com.xmu.course.data.tronclass.auth

/**
 * TronClass 会话存储边界。
 *
 * 上层只依赖此接口，不直接接触 SharedPreferences、Keystore 或任何会话值。
 */
interface TronSessionStore {
    fun saveSession(session: TronSession)

    fun getSession(): TronSession?

    fun clearSession()

    fun hasSession(): Boolean = getSession() != null
}
