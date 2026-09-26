package com.xmu.course.contracts.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderContractsTest {
    private class FakeIdentity(override val id: String) : ProviderIdentity

    @Test
    fun `provider identity carries stable id`() {
        assertEquals("xmu.jw", FakeIdentity("xmu.jw").id)
    }

    @Test
    fun `sync policy defines three levels`() {
        assertEquals(
            setOf(SyncPolicy.MANUAL_ONLY, SyncPolicy.FOREGROUND_ALLOWED, SyncPolicy.BACKGROUND_ALLOWED),
            SyncPolicy.entries.toSet(),
        )
    }

    @Test
    fun `auth state defines four states`() {
        assertEquals(
            setOf(AuthState.AUTHENTICATED, AuthState.AUTH_REQUIRED, AuthState.CHECKING, AuthState.ERROR),
            AuthState.entries.toSet(),
        )
    }

    @Test
    fun `auth state source exposes current state`() {
        var state = AuthState.CHECKING
        val source = object : AuthStateSource {
            override fun currentState(): AuthState = state
        }

        assertEquals(AuthState.CHECKING, source.currentState())

        state = AuthState.AUTHENTICATED
        assertEquals(AuthState.AUTHENTICATED, source.currentState())
    }

    @Test
    fun `provider capability defines four capabilities`() {
        assertEquals(
            setOf(
                ProviderCapability.TIMETABLE,
                ProviderCapability.TODO,
                ProviderCapability.TRANSCRIPT,
                ProviderCapability.CAMPUS_SERVICE,
            ),
            ProviderCapability.entries.toSet(),
        )
    }

    @Test
    fun `privacy data owner clears local data`() = kotlinx.coroutines.runBlocking {
        var cleared = false
        val owner = object : PrivacyDataOwner {
            override suspend fun countLocalData(): Int = 0
            override suspend fun clearLocalData() {
                cleared = true
            }
        }

        owner.clearLocalData()

        assertTrue(cleared)
    }

    @Test
    fun `provider descriptor composes identity capability and policy`() {
        val descriptor = ProviderDescriptor(
            id = "xmu.jw",
            capabilities = setOf(ProviderCapability.TRANSCRIPT, ProviderCapability.CAMPUS_SERVICE),
            syncPolicy = SyncPolicy.MANUAL_ONLY,
        )

        assertEquals("xmu.jw", descriptor.id)
        assertEquals(SyncPolicy.MANUAL_ONLY, descriptor.syncPolicy)
        assertTrue(ProviderCapability.TRANSCRIPT in descriptor.capabilities)
    }

    @Test
    fun `provider descriptor rejects blank id`() {
        val error = runCatching {
            ProviderDescriptor(id = " ", capabilities = emptySet(), syncPolicy = SyncPolicy.MANUAL_ONLY)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}