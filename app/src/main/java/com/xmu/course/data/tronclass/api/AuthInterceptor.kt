package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.auth.TronSession
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 认证拦截边界。
 *
 * 这里不猜测 Authorization、Bearer、Cookie 名称或其他认证格式。具体格式必须由已确认的
 * [RequestAuthenticator] 注入；没有策略时只能继续发送未附加认证信息的请求。
 */
class AuthInterceptor(
    private val sessionProvider: SessionProvider,
    private val requestAuthenticator: RequestAuthenticator,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val session = sessionProvider.getSession()
        val authenticatedRequest = session?.let {
            requestAuthenticator.authenticate(request, it)
        } ?: request
        return chain.proceed(authenticatedRequest)
    }
}

fun interface RequestAuthenticator {
    fun authenticate(request: Request, session: TronSession): Request
}
