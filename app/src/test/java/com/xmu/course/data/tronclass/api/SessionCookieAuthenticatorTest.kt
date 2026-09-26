package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.auth.TronSession
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCookieAuthenticatorTest {
    private val session = TronSession(
        sessionCookieName = "session",
        sessionId = "synthetic-session-value",
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `只注入已确认的session Cookie`() {
        val request = Request.Builder()
            .url("https://lnt.xmu.edu.cn/api/my-courses")
            .header("X-Test", "synthetic")
            .build()

        val authenticated = SessionCookieAuthenticator().authenticate(request, session)

        assertEquals("session=synthetic-session-value", authenticated.header("Cookie"))
        assertNull(authenticated.header("Authorization"))
        assertEquals("synthetic", authenticated.header("X-Test"))
    }

    @Test
    fun `未确认的Cookie名称不会被发送`() {
        val request = Request.Builder()
            .url("https://lnt.xmu.edu.cn/api/my-courses")
            .build()
        val unverified = session.copy(sessionCookieName = "role_token")

        val authenticated = SessionCookieAuthenticator().authenticate(request, unverified)

        assertNull(authenticated.header("Cookie"))
        assertNull(authenticated.header("Authorization"))
    }
}
