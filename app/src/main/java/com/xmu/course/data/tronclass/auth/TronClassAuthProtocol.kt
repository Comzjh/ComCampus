package com.xmu.course.data.tronclass.auth

/**
 * 已由 TronClass 社区实现与模拟器探针共同确认的最小认证协议。
 *
 * TronClass API 使用名为 session 的 Cookie；本项目只保存该 Cookie 的值，
 * 不保存完整 Cookie 串，也不把其它 WebView Cookie 带入原生 API 请求。
 */
const val TRONCLASS_SESSION_COOKIE_NAME = "session"
