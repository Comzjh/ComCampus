package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.auth.TRONCLASS_SESSION_COOKIE_NAME
import com.xmu.course.data.tronclass.auth.TronSession
import okhttp3.Request

/**
 * 按已确认的 TronClass 协议注入最小认证信息。
 *
 * 只发送 session Cookie，不复制 WebView 的完整 Cookie 串，也不构造
 * Authorization/Bearer 头。认证值只存在于当前请求对象的内存生命周期内。
 */
class SessionCookieAuthenticator : RequestAuthenticator {
    override fun authenticate(request: Request, session: TronSession): Request {
        if (session.sessionCookieName != TRONCLASS_SESSION_COOKIE_NAME) return request
        return request.newBuilder()
            .header("Cookie", "$TRONCLASS_SESSION_COOKIE_NAME=${session.sessionId}")
            .build()
    }
}
