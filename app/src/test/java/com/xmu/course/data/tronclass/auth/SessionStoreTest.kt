package com.xmu.course.data.tronclass.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {
    private val storage = FakeSecureStorage()
    private val store = EncryptedTronSessionStore(storage)
    private val session = TronSession(
        sessionCookieName = "verified-session",
        sessionId = "synthetic-session-value",
        createdAt = 1_000L,
        updatedAt = 2_000L,
        expiresAt = 3_000L,
    )

    @Test
    fun `保存会话后可以读取`() {
        store.saveSession(session)

        assertEquals(session, store.getSession())
        assertTrue(store.hasSession())
    }

    @Test
    fun `清除会话后读取为空`() {
        store.saveSession(session)

        store.clearSession()

        assertNull(store.getSession())
        assertFalse(store.hasSession())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `损坏数据安全返回空会话`() {
        storage.values["session_payload"] = "not-json"

        assertNull(store.getSession())
        assertFalse(store.hasSession())
    }

    @Test
    fun `保存无过期时间的会话会清除旧过期字段`() {
        store.saveSession(session)
        store.saveSession(session.copy(expiresAt = null))

        assertEquals(null, store.getSession()?.expiresAt)
    }

    private class FakeSecureStorage : TronSecureStorage {
        val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun removeAll() {
            values.clear()
        }
    }
}
