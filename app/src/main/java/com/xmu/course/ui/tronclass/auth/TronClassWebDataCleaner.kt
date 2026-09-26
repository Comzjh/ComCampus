package com.xmu.course.ui.tronclass.auth

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView

/** 只清理 auth 进程自己的 TronClass WebView 数据，不调用金智教务的清理器。 */
object TronClassWebDataCleaner {
    private const val TRONCLASS_ORIGIN = "https://lnt.xmu.edu.cn"
    private const val TRONCLASS_COOKIE_URL = "$TRONCLASS_ORIGIN/"

    fun clear(context: Context, onComplete: () -> Unit) {
        val webView = WebView(context)
        webView.stopLoading()
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        webView.destroy()

        val cookieManager = CookieManager.getInstance()
        val cookieNames = cookieManager.getCookie(TRONCLASS_COOKIE_URL)
            .orEmpty()
            .split(';')
            .mapNotNull { segment -> segment.substringBefore('=').trim().takeIf(String::isNotEmpty) }
            .distinct()
        val finish = {
            cookieManager.flush()
            // WebView 数据目录已经在 XmuCourseApplication 中按 auth 进程隔离；只删除畅课 origin。
            android.webkit.WebStorage.getInstance().deleteOrigin(TRONCLASS_ORIGIN)
            onComplete()
        }
        if (cookieNames.isEmpty()) {
            finish()
            return
        }
        var remaining = cookieNames.size
        cookieNames.forEach { name ->
            cookieManager.setCookie(TRONCLASS_COOKIE_URL, "$name=; Max-Age=0; Path=/") {
                remaining -= 1
                if (remaining == 0) finish()
            }
        }
    }
}
