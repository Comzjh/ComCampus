package com.xmu.course.contracts.provider

/**
 * Provider 能力声明：声明一个 Provider 可以服务哪些业务域。
 * 只做简单声明，不构成插件系统或注册机制。
 */
enum class ProviderCapability {
    TIMETABLE,
    TODO,
    TRANSCRIPT,
    CAMPUS_SERVICE,
}