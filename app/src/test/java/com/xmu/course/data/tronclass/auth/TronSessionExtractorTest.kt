package com.xmu.course.data.tronclass.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TronSessionExtractorTest {
    @Test
    fun `只提取已验证名称的单个会话字段`() {
        val extractor = TronSessionExtractor("verified-session") { 10_000L }

        val result = extractor.extract(
            "analytics=synthetic-analytics; verified-session=synthetic-session; other=synthetic-other",
        )

        assertEquals("verified-session", result?.sessionCookieName)
        assertEquals("synthetic-session", result?.sessionId)
        assertEquals(10_000L, result?.createdAt)
        assertEquals(10_000L, result?.updatedAt)
    }

    @Test
    fun `没有已验证名称时拒绝提取任意字段`() {
        val extractor = TronSessionExtractor(null) { 10_000L }

        assertNull(extractor.extract("verified-session=synthetic-session"))
    }

    @Test
    fun `缺少目标字段时返回空`() {
        val extractor = TronSessionExtractor("verified-session") { 10_000L }

        assertNull(extractor.extract("other=synthetic-other"))
    }
}
