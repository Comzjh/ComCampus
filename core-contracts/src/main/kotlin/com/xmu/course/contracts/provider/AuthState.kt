package com.xmu.course.contracts.provider

/**
 * 认证状态抽象：只描述"当前是否可用"，不暴露任何凭据、网络、存储或页面机制。
 * 认证实现全部留在 Adapter 侧；Feature 只读取状态。
 *
 * 本轮仅定义契约，不修改现有认证代码。
 */
enum class AuthState {
    AUTHENTICATED,
    AUTH_REQUIRED,
    CHECKING,
    ERROR,
}

/** 只读认证状态源；实现由 Adapter 提供，Feature 不感知实现。 */
interface AuthStateSource {
    fun currentState(): AuthState
}