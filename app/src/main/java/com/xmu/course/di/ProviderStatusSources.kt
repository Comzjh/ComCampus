package com.xmu.course.di

import com.xmu.course.contracts.provider.AuthState
import com.xmu.course.contracts.provider.AuthStateSource
import com.xmu.course.data.auth.AuthStatus

/**
 * Provider 状态源（Phase 5.3.2-A）：把现有 AuthStatus 实现映射为 contracts AuthState。
 *
 * 边界约定：AuthState = contract，AuthStatus = 现有实现；两者并存，
 * 本轮只做 adapter → UI 的只读映射，不重构 Auth，不删除 AuthStatus。
 */
fun AuthStatus.toContractAuthState(): AuthState = when (this) {
    AuthStatus.AUTHENTICATED -> AuthState.AUTHENTICATED
    AuthStatus.AUTH_REQUIRED -> AuthState.AUTH_REQUIRED
    AuthStatus.EXPIRED -> AuthState.AUTH_REQUIRED
    AuthStatus.UNKNOWN -> AuthState.AUTH_REQUIRED
    AuthStatus.CHECKING -> AuthState.CHECKING
}

/**
 * 从快照读取函数构造 AuthStateSource；实现方（组合根）注入 StateFlow 读取，
 * UI 只调用 currentState()，不感知流程细节。无网络请求、无凭据读取。
 */
class FlowAuthStateSource(
    private val readState: () -> AuthState,
) : AuthStateSource {
    override fun currentState(): AuthState = readState()
}