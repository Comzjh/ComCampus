package com.xmu.course.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一次性事件令牌语义（BUG-01 / BUG-02 的竞速防线）。
 */
class OneShotTokenTest {

    @Test
    fun zeroTokenIsNeverClaimableBeforeFirstSuccess() {
        assertFalse(OneShotToken().claim(0L))
    }

    @Test
    fun sameTokenIsClaimableOnlyOnce() {
        val token = OneShotToken()
        assertTrue(token.claim(1L))
        assertFalse(token.claim(1L))
        assertFalse(token.claim(1L))
    }

    @Test
    fun nextSuccessIsClaimableAgain() {
        val token = OneShotToken()
        assertTrue(token.claim(1L))
        assertTrue(token.claim(2L))
        assertFalse(token.claim(2L))
    }

    @Test
    fun twoObserversOfTheSameSessionOnlyNavigateOnce() {
        val token = OneShotToken()
        val current = 3L
        val firstClaims = token.claim(current)
        val secondClaims = token.claim(current)
        assertTrue(firstClaims)
        assertFalse(secondClaims)
    }

    @Test
    fun reReadOfAlreadyConsumedTokenIsRejected() {
        val token = OneShotToken()
        assertTrue(token.claim(5L))
        // 令牌只在真实成功时单调 +1，重复读到同一个值即视为已消费，不再导航。
        assertFalse(token.claim(5L))
    }
}
