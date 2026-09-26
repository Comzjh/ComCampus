package com.xmu.course.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WiseduScopedLogoutTest {
    @Test
    fun `unknown cookie domain keeps logout unsupported`() {
        val plan = WiseduScopedLogoutPlan.unsupportedFromAudit(
            observedCookieUrls = setOf("https://jw.xmu.edu.cn/"),
            observedCookieDomains = emptySet(),
            observedWebStorageOrigins = emptySet(),
        )

        assertFalse(plan.supported)
        assertTrue(plan.cookieDomains.isEmpty())
    }

    @Test
    fun `wisedu plan never includes tronclass scope`() {
        val plan = WiseduScopedLogoutPlan.unsupportedFromAudit(
            observedCookieUrls = setOf("https://jw.xmu.edu.cn/"),
            observedCookieDomains = setOf("jw.xmu.edu.cn"),
            observedWebStorageOrigins = setOf("https://jw.xmu.edu.cn"),
        )

        assertTrue(plan.cookieUrls.none { it.contains("lnt.xmu.edu.cn") })
        assertTrue(plan.cookieDomains.none { it.contains("lnt.xmu.edu.cn") })
        assertTrue(plan.webStorageOrigins.none { it.contains("lnt.xmu.edu.cn") })
    }

    @Test
    fun `unsupported plan performs no cookie or origin mutation`() {
        val cookies = FakeCookieStore()
        val origins = FakeOriginStore()
        val cleaner = WiseduScopedSessionCleaner(
            WiseduScopedLogoutPlan.unsupportedFromAudit(
                observedCookieUrls = setOf("https://jw.xmu.edu.cn/"),
                observedCookieDomains = emptySet(),
                observedWebStorageOrigins = emptySet(),
            ),
            cookies,
            origins,
        )

        assertFalse(cleaner.clear())
        assertTrue(cookies.expired.isEmpty())
        assertTrue(origins.deleted.isEmpty())
    }

    @Test
    fun `supported fake plan expires names only inside audited scope`() {
        val cookies = FakeCookieStore(mapOf("https://jw.xmu.edu.cn/" to listOf("sid", "lang")))
        val origins = FakeOriginStore()
        val cleaner = WiseduScopedSessionCleaner(
            WiseduScopedLogoutPlan(
                supported = true,
                cookieUrls = setOf("https://jw.xmu.edu.cn/"),
                cookieDomains = setOf("jw.xmu.edu.cn"),
                webStorageOrigins = setOf("https://jw.xmu.edu.cn"),
                reason = "audited",
            ),
            cookies,
            origins,
        )

        assertTrue(cleaner.clear())
        assertEquals(
            setOf("https://jw.xmu.edu.cn/" to "sid", "https://jw.xmu.edu.cn/" to "lang"),
            cookies.expired,
        )
        assertEquals(setOf("https://jw.xmu.edu.cn"), origins.deleted)
    }

    private class FakeCookieStore(
        private val values: Map<String, List<String>> = emptyMap(),
    ) : ScopedWiseduCookieStore {
        val expired = mutableSetOf<Pair<String, String>>()

        override fun cookieNames(url: String): List<String> = values[url].orEmpty()

        override fun expire(url: String, name: String) {
            expired += url to name
        }
    }

    private class FakeOriginStore : ScopedWiseduOriginStore {
        val deleted = mutableSetOf<String>()

        override fun deleteOrigin(origin: String) {
            deleted += origin
        }
    }
}
