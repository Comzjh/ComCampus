package com.xmu.course.data.tronclass.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TronClassUiErrorTest {
    @Test
    fun `mapper preserves safe categories and drops integration details`() {
        assertEquals(
            TronClassUiError(TronClassUiErrorCategory.SessionExpired),
            TronClassError.Unauthorized.toUiError(),
        )
        assertEquals(
            TronClassUiError(TronClassUiErrorCategory.AuthRequired),
            TronClassError.SessionMissing.toUiError(),
        )
        assertEquals(
            TronClassUiError(TronClassUiErrorCategory.ParseFailure),
            TronClassError.ParseError(ParseErrorReason.InvalidCourseId).toUiError(),
        )
        assertEquals(
            TronClassUiError(TronClassUiErrorCategory.ServerFailure),
            TronClassError.ServerError(500).toUiError(),
        )
    }

    @Test
    fun `safe messages remain unchanged`() {
        assertEquals("网络连接失败，请检查网络后重试", TronClassUiError(TronClassUiErrorCategory.NetworkFailure).toSafeMessage())
        assertEquals("登录状态已失效，请重新登录", TronClassUiError(TronClassUiErrorCategory.SessionExpired).toSafeMessage())
        assertEquals("请先登录厦大畅课", TronClassUiError(TronClassUiErrorCategory.AuthRequired).toSafeMessage())
        assertEquals("无法确定当前畅课学期，请稍后重试", TronClassUiError(TronClassUiErrorCategory.SemesterUnavailable).toSafeMessage())
    }
}
