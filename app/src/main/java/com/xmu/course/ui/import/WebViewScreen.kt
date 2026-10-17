package com.xmu.course.ui.import

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.data.auth.WiseduAuthConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * 厦大教务 WebView 页面。
 *
 * - 用户自行登录，App 不模拟登录、不保存账密；
 * - Cookie 持久化（onPageFinished / doUpdateVisitedHistory 双点 flush）；
 * - 课表 DOM 就绪后才显示工具栏保存操作，避免遮挡教务页面内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    onBack: () -> Unit,
    onImportCompleted: () -> Unit = {},
    viewModel: ImportViewModel = viewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var hasShownInitialPage by remember { mutableStateOf(false) }
    var initialLoadError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isCheckingTimetable by remember { mutableStateOf(false) }
    var pageGeneration by remember { mutableIntStateOf(0) }
    var finishedPageGeneration by remember { mutableIntStateOf(-1) }
    var canSaveTimetable by remember { mutableStateOf(false) }
    var hasCheckedCurrentPage by remember { mutableStateOf(false) }

    LaunchedEffect(webViewRef, pageGeneration, finishedPageGeneration) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (finishedPageGeneration != pageGeneration) return@LaunchedEffect

        val generation = pageGeneration
        val html = fetchRenderedHtml(webView)
        if (generation == pageGeneration) {
            canSaveTimetable = isTimetableHtml(html)
            hasCheckedCurrentPage = true
        }
    }

    fun saveCurrentTimetable() {
        val webView = webViewRef ?: return
        if (!hasCheckedCurrentPage || isSaving) return

        val generation = pageGeneration
        val isCheckOnly = !canSaveTimetable
        isSaving = true
        isCheckingTimetable = isCheckOnly
        scope.launch {
            try {
                val html = fetchRenderedHtml(webView)
                if (generation != pageGeneration || !isTimetableHtml(html)) {
                    snackbarHostState.showSnackbar("获取课表失败，请返回课表页面后重试")
                } else if (isCheckOnly) {
                    canSaveTimetable = true
                } else {
                    canSaveTimetable = true
                    viewModel.saveCourseHtml(checkNotNull(html))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("获取课表失败，请返回课表页面后重试")
            } finally {
                isCheckingTimetable = false
                isSaving = false
            }
        }
    }

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    if (uiState.shouldShowStartDatePicker) {
        ImportStartDatePickerDialog(
            errorMessage = uiState.startDateError,
            onDismissRequest = viewModel::cancelStartDate,
            onConfirm = viewModel::confirmStartDate,
        )
    }

    uiState.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::cancelOverwrite,
            title = { Text("学期已存在") },
            text = {
                Text("已导入过 ${conflict.semester.name}（${conflict.existingCount} 门课程）。\n是否删除旧课表并导入新的 ${conflict.newCount} 门课程？")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmOverwrite) { Text("覆盖") }
            },
            dismissButton = {
                Button(onClick = viewModel::cancelOverwrite) { Text("取消") }
            },
        )
    }

    LaunchedEffect(uiState.message, uiState.importCompleted) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
            if (uiState.importCompleted) onImportCompleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教务课表") },
                navigationIcon = {
                    IconButton(onClick = {
                        val webView = webViewRef
                        if (webView != null && webView.canGoBack()) webView.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    WebViewTimetableSaveAction(
                        isVisible = hasCheckedCurrentPage,
                        isTimetableReady = canSaveTimetable,
                        isChecking = isCheckingTimetable,
                        isSaving = isSaving,
                        onClick = ::saveCurrentTimetable,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxSize()) {
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
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                pageGeneration += 1
                                finishedPageGeneration = -1
                                canSaveTimetable = false
                                hasCheckedCurrentPage = false
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                hasShownInitialPage = true
                                initialLoadError = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true && !hasShownInitialPage) {
                                    isLoading = false
                                    initialLoadError = true
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?,
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (
                                    request?.isForMainFrame == true && !hasShownInitialPage &&
                                    errorResponse != null && errorResponse.statusCode >= 400
                                ) {
                                    isLoading = false
                                    initialLoadError = true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                if (url == null || view?.url == url) {
                                    finishedPageGeneration = pageGeneration
                                }
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
                        loadUrl(WiseduAuthConfig.TIMETABLE_URL)
                        webViewRef = this
                        }
                    },
                    onRelease = { it.destroy() },
                )
                WebViewInitialLoadingPlaceholder(
                    isVisible = !hasShownInitialPage,
                    hasError = initialLoadError,
                    onRetry = {
                        val webView = webViewRef ?: return@WebViewInitialLoadingPlaceholder
                        initialLoadError = false
                        isLoading = true
                        loadProgress = 0
                        webView.reload()
                    },
                )
            }
        }
    }
}

@Composable
internal fun WebViewTimetableSaveAction(
    isVisible: Boolean,
    isTimetableReady: Boolean,
    isChecking: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    if (!isVisible) return

    IconButton(
        onClick = onClick,
        enabled = !isSaving,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("webview_save_timetable")
            .semantics {
                contentDescription = when {
                    isChecking -> "正在检查课表"
                    isSaving -> "正在保存课表"
                    isTimetableReady -> "保存课表"
                    else -> "重新检查课表"
                }
            },
    ) {
        if (isChecking || isSaving) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (isTimetableReady) {
            Icon(Icons.Filled.Save, contentDescription = null)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null)
        }
    }
}

@Composable
internal fun WebViewInitialLoadingPlaceholder(
    isVisible: Boolean,
    hasError: Boolean = false,
    onRetry: () -> Unit = {},
) {
    if (!isVisible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("webview_initial_loading"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (hasError) {
                Text("教务页面加载失败", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "请检查网络连接后重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRetry, modifier = Modifier.testTag("webview_initial_retry")) {
                    Text("重新加载")
                }
            } else {
                CircularProgressIndicator()
                Text("正在加载教务页面", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "页面显示后即可继续操作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** evaluateJavascript 的 suspend 封装，返回解码后的 HTML。 */
private suspend fun evaluateDom(webView: WebView): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                webView.evaluateJavascript("document.documentElement.outerHTML") { result ->
                    if (cont.isActive) {
                        cont.resume(
                            runCatching { JSONObject("""{"h":$result}""").getString("h") }.getOrNull(),
                        )
                    }
                }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

/**
 * 轮询等待金智 AJAX 渲染完成：DOM 出现课程节点（arrage）或日期表（kbckBottom）即认为就绪。
 * 最多等待 10 次 × 1.2s，避免用户在错误页面时无限等待。
 */
internal fun isTimetableHtml(html: String?): Boolean =
    html?.contains("arrage") == true || html?.contains("kbckBottom") == true

private suspend fun fetchRenderedHtml(webView: WebView): String? {
    repeat(10) {
        val html = runCatching { evaluateDom(webView) }.getOrNull()
        if (isTimetableHtml(html)) {
            return html
        }
        delay(1200)
    }
    return null
}
