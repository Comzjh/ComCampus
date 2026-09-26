package com.xmu.course.ui.tronclass.auth

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.xmu.course.data.tronclass.auth.EncryptedTronSessionStore
import com.xmu.course.data.tronclass.auth.TronSessionExtractor

/**
 * TronClass 专用 SSO WebView。
 *
 * 用户直接在厦大统一认证页面输入信息；Activity 不读取表单、不注入脚本、不记录凭据。
 * 只有回到已知 host/path 且提取到已验证的单个会话 Cookie 时才返回 RESULT_OK。
 */
class TronClassAuthActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private val sessionStore by lazy { EncryptedTronSessionStore(applicationContext) }
    private val sessionExtractor by lazy {
        TronSessionExtractor(
            intent.getStringExtra(EXTRA_VERIFIED_SESSION_COOKIE_NAME)
                ?: TRONCLASS_SESSION_COOKIE_NAME,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_CLEAR_TRONCLASS_AUTH, false)) {
            clearAuthAndFinish()
            return
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            showUnsupportedVersionScreen()
            return
        }
        createWebView()
    }

    private fun showUnsupportedVersionScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply {
            text = "当前系统版本暂不支持畅课登录"
            textSize = 20f
            gravity = android.view.Gravity.CENTER
        }
        val description = TextView(this).apply {
            text = "畅课登录需要 Android 9(API 28)及以上版本，请升级系统后使用。"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 20, 0, 24)
        }
        val backButton = Button(this).apply {
            text = "返回"
            setOnClickListener {
                setResult(RESULT_SESSION_UNAVAILABLE)
                finish()
            }
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            description,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(root)
    }

    private fun createWebView() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val webContent = FrameLayout(this)
        statusView = TextView(this).apply {
            text = "正在打开厦大统一身份认证…"
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFFF3F5F7.toInt())
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val progress = ProgressBar(this)
        webView = WebView(this)
        webContent.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        webContent.addView(
            progress,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = android.view.Gravity.CENTER
            },
        )
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            webContent,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(root)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            saveFormData = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.visibility = if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                statusView.text = "正在完成厦大统一身份认证…"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isFinishing) inspectLoginState(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) statusView.text = "认证页面加载失败，请检查网络后重试"
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse,
            ) {
                if (request.isForMainFrame) statusView.text = "认证服务暂时不可用，请稍后重试"
            }
        }
        webView.loadUrl(TRONCLASS_COURSES_URL)
    }

    private fun inspectLoginState(url: String?) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (!TronAuthPagePolicy.isCoursesPage(uri)) return

        val cookieHeader = CookieManager.getInstance()
            .getCookie("https://${TronAuthPagePolicy.TRONCLASS_HOST}")
        val session = sessionExtractor.extract(cookieHeader)
        if (session == null) {
            statusView.text = "已返回畅课页面，但暂未识别到已验证会话"
            return
        }

        runCatching { sessionStore.saveSession(session) }
            .onSuccess {
                setResult(RESULT_OK)
                finish()
            }
            .onFailure {
                statusView.text = "会话安全保存失败，请重试"
            }
    }

    private fun clearAuthAndFinish() {
        val storeCleared = runCatching { sessionStore.clearSession() }.isSuccess
        TronClassWebDataCleaner.clear(this) {
            setResult(if (storeCleared) RESULT_OK else RESULT_CLEAR_TRONCLASS_AUTH_FAILED)
            finish()
        }
    }

    @Deprecated("Deprecated in Android API 33; retained for minSdk compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
        super.onDestroy()
    }
}
