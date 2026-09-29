package com.xmu.course.ui.auth

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xmu.course.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONObject
import com.xmu.course.AppLinks
import com.xmu.course.adapter.jw.JwAcademicPdfDownload
import com.xmu.course.adapter.jw.JwAcademicReportDownloadHandler
import com.xmu.course.adapter.jw.JwGsappSessionPriming
import com.xmu.course.data.academiccompletion.AcademicDiagnosticEnvironment
import com.xmu.course.data.academiccompletion.AcademicRefreshDiagnosticArchive
import com.xmu.course.ui.import.WebSessionManager
import java.io.File

private const val JW_ACADEMIC_REPORT_TAG = "JwAcademicReport"
private val PRINT_DIAGNOSTIC_PATTERN = Regex("print|pdf|report|xywccx", RegexOption.IGNORE_CASE)

/**
 * 独立的 JW 学业完成查询入口；只承载官方页面和用户触发的下载事件。
 *
 * Adapter 边界说明：本页面属于 JW Adapter 的 WebView 承载层，只做用户手动触发的
 * 页面加载与一次性下载事件回调；不自动点击、不后台访问、不读取 Cookie/Token。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwAcademicReportScreen(
    onBack: () -> Unit,
    onPdfDownloaded: (Uri) -> Unit = {},
    pageUrl: String = AppLinks.JW_ACADEMIC_REPORT_URL,
    pageTitle: String = "学业完成查询",
    refreshController: JwAcademicRefreshController? = null,
    /** 学业首页一次性点击刷新：仍由用户手势触发（MANUAL_ONLY），落回查询页后自动执行一次。 */
    autoRefresh: Boolean = false,
    onAutoRefreshFinished: () -> Unit = {},
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    // gsapp 一次性会话引导：仅挂载刷新控制器的「官方学业完成查询」页启用；每个 WebView 至多一次。
    var primingActive by remember { mutableStateOf(refreshController != null) }
    // 自动刷新请求至多消费一次；证书页（无控制器）天然不触发。
    var autoRefreshPending by remember { mutableStateOf(autoRefresh && refreshController != null) }
    val context = LocalContext.current
    val diagnosticArchive = remember(context) {
        AcademicRefreshDiagnosticArchive(
            outputDirectory = File(context.filesDir, "diagnostics/academic_refresh"),
            environment = AcademicDiagnosticEnvironment(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                androidApiLevel = Build.VERSION.SDK_INT,
            ),
        )
    }
    var diagnosticArchiveFile by remember(diagnosticArchive) {
        mutableStateOf(diagnosticArchive.latestArchive.takeIf { it.isFile })
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshScope = rememberCoroutineScope()
    val downloadHandler = remember(onPdfDownloaded) {
        JwAcademicReportDownloadHandler(onPdfDownloaded)
    }

    suspend fun refreshAndShowResult(controller: JwAcademicRefreshController, view: WebView) {
        val result = runCatching {
            controller.refresh { script -> view.evaluateJwScript(script) }
        }.getOrElse { JwAcademicRefreshResult("刷新失败，本机数据保持不变") }
        isRefreshing = false
        val savedArchive = result.diagnostics?.let { diagnostic ->
            runCatching {
                withContext(Dispatchers.IO) { diagnosticArchive.write(diagnostic) }
            }.getOrNull()
        }
        if (result.diagnostics != null) diagnosticArchiveFile = savedArchive

        val message = when {
            savedArchive != null -> "${result.message}；已保存脱敏诊断包（含汇总学分）"
            result.diagnostics != null -> "${result.message}；诊断包保存失败"
            else -> result.message
        }
        val snackbarResult = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (savedArchive != null) "分享诊断包" else null,
            withDismissAction = savedArchive != null,
            duration = if (result.isWarning) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (snackbarResult == SnackbarResult.ActionPerformed && savedArchive != null) {
            runCatching { shareDiagnosticArchive(context, savedArchive) }
                .onFailure { snackbarHostState.showSnackbar("无法打开分享面板") }
        }
    }

    fun navigateBack() {
        val webView = webViewRef
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::navigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(onClick = ::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    diagnosticArchiveFile?.let { archive ->
                        IconButton(
                            onClick = {
                                runCatching { shareDiagnosticArchive(context, archive) }
                                    .onFailure {
                                        refreshScope.launch {
                                            snackbarHostState.showSnackbar("无法打开分享面板")
                                        }
                                    }
                            },
                            modifier = Modifier.testTag("jw_academic_diagnostic_share_button"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "分享脱敏诊断包")
                        }
                    }
                    // 仅官方学业完成查询页挂载刷新控制器；证书申请等页面无此动作。
                    if (refreshController != null) {
                        IconButton(
                            enabled = !isRefreshing,
                            onClick = {
                                val view = webViewRef
                                if (view == null) {
                                    refreshScope.launch {
                                        snackbarHostState.showSnackbar("页面尚未就绪，请稍候再试")
                                    }
                                } else {
                                    isRefreshing = true
                                    refreshScope.launch {
                                        refreshAndShowResult(refreshController, view)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("jw_academic_refresh_button"),
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新学业数据")
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isHorizontalScrollBarEnabled = true
                    isVerticalScrollBarEnabled = true
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        saveFormData = false
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportMultipleWindows(true)
                    }
                    WebSessionManager.attach(this)
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                            Log.d(
                                JW_ACADEMIC_REPORT_TAG,
                                "console level=${message.messageLevel()} line=${message.lineNumber()} " +
                                    "source=${sanitizeDiagnosticUrl(message.sourceId())} " +
                                    "message=${sanitizeDiagnosticText(message.message())}",
                            )
                            return super.onConsoleMessage(message)
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            Log.d(JW_ACADEMIC_REPORT_TAG, "title=${sanitizeDiagnosticText(title)}")
                            super.onReceivedTitle(view, title)
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?,
                        ): Boolean {
                            val currentWebView = view
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            if (currentWebView == null || transport == null) {
                                Log.w(
                                    JW_ACADEMIC_REPORT_TAG,
                                    "popup rejected missingView=${currentWebView == null} " +
                                        "missingTransport=${transport == null}",
                                )
                                return false
                            }
                            transport.webView = currentWebView
                            resultMsg.sendToTarget()
                            Log.d(
                                JW_ACADEMIC_REPORT_TAG,
                                "popup forwarded dialog=$isDialog userGesture=$isUserGesture",
                            )
                            return true
                        }
                    }
                    Log.d(
                        JW_ACADEMIC_REPORT_TAG,
                        "settings js=${settings.javaScriptEnabled} domStorage=${settings.domStorageEnabled} " +
                            "wideViewport=${settings.useWideViewPort} overview=${settings.loadWithOverviewMode} " +
                            "ua=${sanitizeDiagnosticText(settings.userAgentString)}",
                    )
                    setDownloadListener { url, _, _, mimeType, _ ->
                        downloadHandler.handle(
                            JwAcademicPdfDownload(
                                uri = Uri.parse(url),
                                mimeType = mimeType,
                            ),
                        )
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            Log.d(
                                JW_ACADEMIC_REPORT_TAG,
                                "navigationOverride url=${sanitizeDiagnosticUrl(request.url?.toString())}",
                            )
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?,
                        ): Boolean {
                            Log.d(
                                JW_ACADEMIC_REPORT_TAG,
                                "navigationOverrideLegacy url=${sanitizeDiagnosticUrl(url)}",
                            )
                            return super.shouldOverrideUrlLoading(view, url)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): android.webkit.WebResourceResponse? {
                            val requestUrl = request.url?.toString().orEmpty()
                            if (request.isForMainFrame ||
                                PRINT_DIAGNOSTIC_PATTERN.containsMatchIn(requestUrl)
                            ) {
                                Log.d(
                                    JW_ACADEMIC_REPORT_TAG,
                                    "resourceRequest mainFrame=${request.isForMainFrame} " +
                                        "url=${sanitizeDiagnosticUrl(requestUrl)}",
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                            Log.d(
                                JW_ACADEMIC_REPORT_TAG,
                                "pageFinished url=${sanitizeDiagnosticUrl(url)} title=${sanitizeDiagnosticText(view?.title)}",
                            )
                            WebSessionManager.flushCookies()
                            if (primingActive) {
                                when (JwGsappSessionPriming.landingFor(url, pageUrl)) {
                                    JwGsappSessionPriming.Landing.GsappReady -> {
                                        // gsapp 会话已建立：自动返回学业查询页，不再重复引导。
                                        primingActive = false
                                        Log.d(JW_ACADEMIC_REPORT_TAG, "gsappPriming=done return=report")
                                        view?.loadUrl(pageUrl)
                                        view?.clearHistory()
                                    }
                                    JwGsappSessionPriming.Landing.ReportPageReached -> {
                                        primingActive = false
                                        Log.d(JW_ACADEMIC_REPORT_TAG, "gsappPriming=reportReached")
                                    }
                                    JwGsappSessionPriming.Landing.Hold -> {
                                        // 官方登录页等落点：停下由用户手动处理；不自动导航、不绕过认证。
                                        Log.d(JW_ACADEMIC_REPORT_TAG, "gsappPriming=holdForUser")
                                    }
                                }
                            }
                            val controller = refreshController
                            if (
                                autoRefreshPending && !isRefreshing && !primingActive &&
                                controller != null && view != null &&
                                JwGsappSessionPriming.landingFor(url, pageUrl) ==
                                JwGsappSessionPriming.Landing.ReportPageReached
                            ) {
                                // 一次性自动编排双源刷新；失败保旧缓存语义与手动按钮完全一致。
                                autoRefreshPending = false
                                isRefreshing = true
                                Log.d(JW_ACADEMIC_REPORT_TAG, "autoRefresh=start")
                                refreshScope.launch {
                                    refreshAndShowResult(controller, view)
                                    Log.d(JW_ACADEMIC_REPORT_TAG, "autoRefresh=done")
                                    kotlinx.coroutines.delay(1_500)
                                    onAutoRefreshFinished()
                                }
                            }
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            super.onPageStarted(view, url, favicon)
                            Log.d(JW_ACADEMIC_REPORT_TAG, "pageStarted url=${sanitizeDiagnosticUrl(url)}")
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request.isForMainFrame) {
                                Log.w(
                                    JW_ACADEMIC_REPORT_TAG,
                                    "mainFrameError code=${error.errorCode} " +
                                        "description=${sanitizeDiagnosticText(error.description)} " +
                                        "url=${sanitizeDiagnosticUrl(request.url?.toString())}",
                                )
                                if (primingActive) {
                                    // 引导失败：fail-closed 回学业查询页；不重试引导、不绕过认证。
                                    primingActive = false
                                    Log.w(JW_ACADEMIC_REPORT_TAG, "gsappPriming=failed fallback=report")
                                    view.loadUrl(pageUrl)
                                    view.clearHistory()
                                }
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            errorResponse: android.webkit.WebResourceResponse,
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request.isForMainFrame) {
                                Log.w(
                                    JW_ACADEMIC_REPORT_TAG,
                                    "mainFrameHttpError status=${errorResponse.statusCode} " +
                                        "reason=${sanitizeDiagnosticText(errorResponse.reasonPhrase)} " +
                                        "url=${sanitizeDiagnosticUrl(request.url?.toString())}",
                                )
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: android.webkit.SslErrorHandler,
                            error: android.net.http.SslError,
                        ) {
                            Log.w(
                                JW_ACADEMIC_REPORT_TAG,
                                "sslError primary=${error.primaryError} " +
                                    "url=${sanitizeDiagnosticUrl(error.url)}",
                            )
                            super.onReceivedSslError(view, handler, error)
                        }
                    }
                    if (primingActive) {
                        // 先顶层导航 gsapp 官方页建立子应用会话，落点由 onPageFinished 判定。
                        Log.d(JW_ACADEMIC_REPORT_TAG, "gsappPriming=start")
                        loadUrl(JwGsappSessionPriming.PRIMING_URL)
                    } else {
                        loadUrl(pageUrl)
                    }
                    webViewRef = this
                }
            },
            update = { canGoBack = it.canGoBack() },
            onRelease = { it.destroy() },
        )
    }
}

private fun shareDiagnosticArchive(context: Context, archive: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        archive,
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "脱敏诊断包", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "分享脱敏诊断包"))
}

private fun sanitizeDiagnosticUrl(value: String?): String =
    value
        ?.replace(Regex("([?&](?:token|ticket|password|session|code|secret)=)[^&]*", RegexOption.IGNORE_CASE), "$1[redacted]")
        ?.take(300)
        ?: "<none>"

private fun sanitizeDiagnosticText(value: CharSequence?): String =
    value
        ?.toString()
        ?.replace(Regex("(?i)(password|token|cookie|ticket|session|secret)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
        ?.take(300)
        ?: "<none>"

/** evaluateJavascript 的 suspend 封装；必须在主线程调用并容忍页面销毁竞态。 */
private suspend fun WebView.evaluateJwScript(script: String): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                evaluateJavascript(script) { raw ->
                    if (cont.isActive) cont.resume(decodeJsResult(raw))
                }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

/** 解码 evaluateJavascript 返回值：字符串字面量去引号；对象/数值 JSON 原样透传。 */
internal fun decodeJsResult(raw: String?): String? {
    val value = raw?.takeIf { it != "null" && it.isNotBlank() } ?: return null
    return if (value.startsWith("\"")) {
        runCatching { JSONObject("""{"v":$value}""").getString("v") }.getOrDefault(value)
    } else {
        value
    }
}
