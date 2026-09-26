package com.xmu.course.ui.auth

import com.xmu.course.data.auth.AuthStatus
import com.xmu.course.data.auth.ConservativeWiseduAuthVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JwAuthScreenTest {
    @Test
    fun `auth entry is the official root and observation strips query`() {
        assertEquals("https://jw.xmu.edu.cn/", XMU_JW_AUTH_URL)

        val observation = wiseduObservationForUrl(
            "https://jw.xmu.edu.cn/new/index.html?redacted=value#fragment",
        )

        assertEquals("jw.xmu.edu.cn", observation.host)
        assertEquals("/new/index.html", observation.path)
        assertNull(observation.pageTitle)
        assertNull(observation.httpStatus)
        assertFalse(observation.authenticatedSignal)
        assertEquals(AuthStatus.UNKNOWN, ConservativeWiseduAuthVerifier.verify(observation))
    }
}
