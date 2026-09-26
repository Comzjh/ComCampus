package com.xmu.course.di

import com.xmu.course.contracts.provider.AuthState
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.data.auth.AuthStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderStatusTest {
    @Test
    fun authStatusMapsToContractAuthState() {
        assertEquals(AuthState.AUTHENTICATED, AuthStatus.AUTHENTICATED.toContractAuthState())
        assertEquals(AuthState.AUTH_REQUIRED, AuthStatus.AUTH_REQUIRED.toContractAuthState())
        assertEquals(AuthState.AUTH_REQUIRED, AuthStatus.EXPIRED.toContractAuthState())
        assertEquals(AuthState.AUTH_REQUIRED, AuthStatus.UNKNOWN.toContractAuthState())
        assertEquals(AuthState.CHECKING, AuthStatus.CHECKING.toContractAuthState())
    }

    @Test
    fun flowAuthStateSourceReadsSnapshotLazily() {
        var current = AuthState.CHECKING
        val source = FlowAuthStateSource { current }

        assertEquals(AuthState.CHECKING, source.currentState())

        current = AuthState.AUTHENTICATED
        assertEquals(AuthState.AUTHENTICATED, source.currentState())
    }

    @Test
    fun providerDescriptorKeepsPureMachineIdentityFields() {
        // 架构护栏（与 ProviderDescriptorArchitectureTest 呼应）：
        // 状态必须走 statusSource，而不是进入 descriptor 字段。
        val fields = ProviderDescriptor::class.java.declaredFields
            .map { it.name }
            .filterNot { it == "serialVersionUID" || it.startsWith("$") }

        assertEquals(listOf("id", "capabilities", "syncPolicy"), fields)
    }
}