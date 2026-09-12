package com.xmu.course.ui.import

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * WebView 登录会话管理。
 *
 * 设计原则：
 * - 永不保存用户名/密码，登录完全由用户在 WebView 中完成；
 * - 登录态依赖 android.webkit.CookieManager 的持久化 Cookie（App 私有目录）；
 * - 提供统一的“清除登录状态”入口（Cookie + WebView 缓存 + Web 存储）。
 */
object WebSessionManager {

    /** WebView 创建后调用：允许接收 Cookie（含教务系统跳转涉及的第三方 Cookie）。 */
    fun attach(webView: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    /** 页面加载完成后调用：把内存中的 Cookie 落盘，保证重启后仍保持登录。 */
    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    /**
     * 清除登录状态：Cookie、WebView 缓存、Web 存储（localStorage 等）。
     * 用户数据文件（如已保存的课表 HTML）不受影响。
     */
    fun clear(context: Context, webView: WebView? = null) {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            removeSessionCookies(null)
            flush()
        }
        webView?.apply {
            clearCache(true)
            clearFormData()
            clearHistory()
        }
        WebStorage.getInstance().deleteAllData()
        context.cacheDir.deleteRecursively()
    }
}
