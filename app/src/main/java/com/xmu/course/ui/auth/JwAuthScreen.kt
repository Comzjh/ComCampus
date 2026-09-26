package com.xmu.course.ui.auth

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xmu.course.data.auth.WiseduAuthObservation
import com.xmu.course.data.auth.WiseduApiResponseObservation
import com.xmu.course.data.auth.WiseduAuthenticatedApiSignal
import com.xmu.course.ui.import.WebSessionManager
import kotlinx.coroutines.delay

const val XMU_JW_AUTH_URL = "https://jw.xmu.edu.cn/"

/**
 * 厦大教务认证页面。
 *
 * 该页面只负责承载官方 WebView 登录和匿名导航观察，不抓取课表 DOM、不调用 parser、
 * 不写课程数据。它运行在主进程，因此与课表导入复用同一个 WebView CookieManager 数据目录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwAuthScreen(
    onBack: () -> Unit,
    onObservation: (WiseduAuthObservation) -> Unit = {},
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("正在打开厦大教务页面…") }

    fun navigateBack() {
        val webView = webViewRef
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::navigateBack)

    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        while (true) {
            WiseduAuthApiProbe.probe(webView) { apiObservation ->
                val signal = WiseduAuthenticatedApiSignal.verify(apiObservation)
                onObservation(
                    WiseduAuthObservation(
                        host = apiObservation.host,
                        path = apiObservation.path,
                        httpStatus = apiObservation.httpStatus,
                        authenticatedSignal = signal == com.xmu.course.data.auth.AuthStatus.AUTHENTICATED,
                        authRequiredSignal = signal == com.xmu.course.data.auth.AuthStatus.AUTH_REQUIRED,
                    ),
                )
                statusText = when (signal) {
                    com.xmu.course.data.auth.AuthStatus.AUTHENTICATED -> "教务会话已通过认证接口验证"
                    com.xmu.course.data.auth.AuthStatus.AUTH_REQUIRED -> "教务会话需要登录"
                    else -> "学校页面已打开；认证状态待验证"
                }
            }
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("厦大教务") },
                navigationIcon = {
                    IconButton(onClick = ::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            saveFormData = false
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(false)
                        }
                        WebSessionManager.attach(this)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                statusText = "正在完成页面跳转…"
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                canGoBack = view?.canGoBack() == true
                                statusText = "学校页面已打开；认证状态将在取得真实信号后更新"
                                WebSessionManager.flushCookies()
                                onObservation(wiseduObservationForUrl(url))
                            }


                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    statusText = "教务页面加载失败，请检查网络后重试"
                                }
                            }

                        }
                        loadUrl(XMU_JW_AUTH_URL)
                        webViewRef = this
                    }
                },
                update = { canGoBack = it.canGoBack() },
                onRelease = { it.destroy() },
            )
        }
    }
}

internal fun wiseduObservationForUrl(url: String?): WiseduAuthObservation {
    val uri = runCatching { url?.let { java.net.URI(it) } }.getOrNull()
    return WiseduAuthObservation(
        host = uri?.host,
        path = uri?.path,
    )
}
