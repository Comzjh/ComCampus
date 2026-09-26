package com.xmu.course.data.auth

import org.junit.Assert.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WiseduAuthObservationTest {
    @Test
    fun `conservative verifier keeps unresolved observation unknown`() {
        val observation = WiseduAuthObservation(
            host = "jw.xmu.edu.cn",
            path = "/new/index.html",
        )

        assertEquals(AuthStatus.UNKNOWN, ConservativeWiseduAuthVerifier.verify(observation))
    }

    @Test
    fun `conservative verifier accepts only explicit anonymous authenticated signal`() {
        val observation = WiseduAuthObservation(
            host = "jw.xmu.edu.cn",
            path = "/verified-route",
            authenticatedSignal = true,
        )

        assertEquals(AuthStatus.AUTHENTICATED, ConservativeWiseduAuthVerifier.verify(observation))
    }

    @Test
    fun `conservative verifier preserves required and expired states`() {
        assertEquals(
            AuthStatus.AUTH_REQUIRED,
            ConservativeWiseduAuthVerifier.verify(WiseduAuthObservation(authRequiredSignal = true)),
        )
        assertEquals(
            AuthStatus.EXPIRED,
            ConservativeWiseduAuthVerifier.verify(WiseduAuthObservation(expiredSignal = true)),
        )
    }

    @Test
    fun `cookie-like metadata alone remains unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            ConservativeWiseduAuthVerifier.verify(
                WiseduAuthObservation(host = "jw.xmu.edu.cn", path = "/", httpStatus = 200),
            ),
        )
    }

    @Test
    fun `unexpected route without explicit signal remains unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            ConservativeWiseduAuthVerifier.verify(
                WiseduAuthObservation(host = "jw.xmu.edu.cn", path = "/unexpected", httpStatus = 500),
            ),
        )
    }

    @Test
    fun `auth required wins over expired only when no authenticated signal`() {
        assertEquals(
            AuthStatus.EXPIRED,
            ConservativeWiseduAuthVerifier.verify(
                WiseduAuthObservation(authRequiredSignal = true, expiredSignal = true),
            ),
        )
    }

    @Test
    fun `legacy marker cannot seed a new runtime source`() = runTest {
        val marker = FakeMarker(verified = true)
        val source = WiseduAuthStatusSource(marker)

        assertEquals(AuthStatus.UNKNOWN, source.check())
        assertEquals(AuthStatus.UNKNOWN, WiseduAuthStatusSource(marker).check())
    }

    @Test
    fun `real auth required observation overrides legacy marker`() = runTest {
        val marker = FakeMarker(verified = true)
        val source = WiseduAuthStatusSource(marker)

        assertEquals(
            AuthStatus.AUTH_REQUIRED,
            source.applyObservation(WiseduAuthObservation(authRequiredSignal = true)),
        )
        assertEquals(AuthStatus.AUTH_REQUIRED, source.check())
    }

    @Test
    fun `authenticated current user api json response is authenticated`() {
        assertEquals(
            AuthStatus.AUTHENTICATED,
            WiseduAuthenticatedApiSignal.verify(apiResponse(contentType = "application/json")),
        )
    }

    @Test
    fun `authenticated current user api accepts json charset`() {
        assertEquals(
            AuthStatus.AUTHENTICATED,
            WiseduAuthenticatedApiSignal.verify(apiResponse(contentType = "application/json; charset=UTF-8")),
        )
    }

    @Test
    fun `current user api html response is auth required`() {
        assertEquals(
            AuthStatus.AUTH_REQUIRED,
            WiseduAuthenticatedApiSignal.verify(apiResponse(contentType = "text/html")),
        )
    }

    @Test
    fun `current user api redirect is unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            WiseduAuthenticatedApiSignal.verify(apiResponse(redirected = true)),
        )
    }

    @Test
    fun `current user api network failure is unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            WiseduAuthenticatedApiSignal.verify(apiResponse(networkError = true)),
        )
    }

    @Test
    fun `unexpected json endpoint is unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            WiseduAuthenticatedApiSignal.verify(apiResponse(path = "/unexpected.do")),
        )
    }

    @Test
    fun `unexpected html shell is unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            WiseduAuthenticatedApiSignal.verify(
                apiResponse(host = "jw.xmu.edu.cn", path = "/new/index.html", contentType = "text/html"),
            ),
        )
    }

    @Test
    fun `non GET current user response is unknown`() {
        assertEquals(
            AuthStatus.UNKNOWN,
            WiseduAuthenticatedApiSignal.verify(apiResponse(method = "POST")),
        )
    }

    private fun apiResponse(
        method: String = "GET",
        host: String = WiseduAuthenticatedApiSignal.HOST,
        path: String = WiseduAuthenticatedApiSignal.PATH,
        httpStatus: Int? = 200,
        contentType: String? = "application/json",
        redirected: Boolean = false,
        networkError: Boolean = false,
    ) = WiseduApiResponseObservation(
        method = method,
        host = host,
        path = path,
        httpStatus = httpStatus,
        contentType = contentType,
        redirected = redirected,
        networkError = networkError,
    )

    private class FakeMarker(private val verified: Boolean) : WiseduSessionMarker {
        override fun isVerified(): Boolean = verified
        override fun markVerified() = Unit
        override fun clear() = Unit
    }
}
