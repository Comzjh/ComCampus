package com.xmu.course.ui.import

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/** 厦门大学金智教务系统“我的课表”入口。 */
const val XMU_JW_URL = "https://jw.xmu.edu.cn/gsapp/sys/wdkbapp/*default/index.do#/xskcb"

private const val TAG = "XmuImport"

/**
 * 厦大教务 WebView 页面。
 *
 * - 用户自行登录，App 不模拟登录、不保存账密；
 * - Cookie 持久化（onPageFinished / doUpdateVisitedHistory 双点 flush）；
 * - FAB 抓取 DOM：轮询等待 AJAX 渲染完成（DOM 出现 arrage 节点）后再交给 ViewModel。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val webView = webViewRef ?: return@FloatingActionButton
                isSaving = true
                scope.launch {
                    val html = fetchRenderedHtml(webView)
                    isSaving = false
                    if (html.isNullOrBlank()) {
                        Log.w(TAG, "抓取 DOM 失败")
                        snackbarHostState.showSnackbar("获取页面失败，请确认已打开课表页面")
                    } else {
                        viewModel.saveCourseHtml(html)
                    }
                }
            }) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.padding(12.dp))
                } else {
                    Icon(Icons.Filled.Save, contentDescription = "保存课表")
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            // 教务页面登录跳转后避免命中过期缓存（Bug 2）。
                            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        }
                        WebSessionManager.attach(this)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                isLoading = newProgress in 1..99
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                WebSessionManager.flushCookies()
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                                // CAS 登录跳转链中途也可能产生 Cookie，双保险落盘。
                                WebSessionManager.flushCookies()
                            }
                        }
                        loadUrl(XMU_JW_URL)
                        webViewRef = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

/** evaluateJavascript 的 suspend 封装，返回解码后的 HTML。 */
private suspend fun evaluateDom(webView: WebView): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript("document.documentElement.outerHTML") { result ->
                cont.resume(
                    runCatching { JSONObject("""{"h":$result}""").getString("h") }.getOrNull(),
                )
            }
        }
    }

/**
 * 轮询等待金智 AJAX 渲染完成：DOM 出现课程节点（arrage）或日期表（kbckBottom）即认为就绪。
 * 最多等待 10 次 × 1.2s，避免用户在错误页面时无限等待。
 */
private suspend fun fetchRenderedHtml(webView: WebView): String? {
    repeat(10) { attempt ->
        val html = evaluateDom(webView)
        if (html != null && html.contains("arrage")) {
            Log.d(TAG, "DOM 就绪 attempt=$attempt length=${html.length}")
            return html
        }
        Log.d(TAG, "等待 AJAX 渲染 attempt=$attempt length=${html?.length ?: 0}")
        delay(1200)
    }
    return null
}


