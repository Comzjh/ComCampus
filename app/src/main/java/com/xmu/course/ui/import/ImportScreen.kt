package com.xmu.course.ui.import

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppListDivider
import com.xmu.course.ui.components.AppNavigationRow
import java.time.Instant
import java.util.Locale

internal const val XMU_ACADEMIC_CALENDAR_URL =
    "https://law.xmu.edu.cn/virtual_attach_file.vsb?afc=8olMiVM4r7Ulr7MD4MVL4M7L8Q7MmGssM8C8Lmfko7C4MmU0gihFp2hmCIa0U1yDU1h2LYyaMNCPUmW2LRrfLlMfM4NDLNQfMm-4L8LYL4VFoRrRM7UbL4fFLl-4MmlJv2nto4OeoDQZ_2C0qIbtpYyPLRLbg4LaLR-bLSbw62Z8c&e=.pdf&nid=300291&oid=1474610936&tid=1039"

private val DEFAULT_IMPORT_START_DATE_MILLIS =
    Instant.parse("2026-09-07T00:00:00Z").toEpochMilli()

/**
 * 导入入口页。
 *
 * - 打开厦大教务 WebView 登录并“保存课表”；
 * - 或选择本地 HTML 文件导入（测试/离线场景）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onOpenWebView: () -> Unit,
    onBack: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onImportCompleted: () -> Unit = {},
    viewModel: ImportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.shouldShowStartDatePicker) {
        ImportStartDatePickerDialog(
            errorMessage = uiState.startDateError,
            onDismissRequest = viewModel::cancelStartDate,
            onConfirm = viewModel::confirmStartDate,
        )
    }

    // 同学期覆盖导入确认对话框
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
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val html = readHtmlFromUri(context, uri)
            if (html != null) {
                viewModel.saveImportedHtml(html)
            } else {
                viewModel.saveImportedHtml("")
            }
        }
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
                title = { Text("导入课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        ImportOptionsContent(
            modifier = Modifier.padding(innerPadding),
            onOpenWebView = onOpenWebView,
            onOpenGuide = onOpenGuide,
            onPickHtml = { filePicker.launch(arrayOf("text/html")) },
        )
    }
}

@Composable
internal fun ImportOptionsContent(
    modifier: Modifier = Modifier,
    onOpenWebView: () -> Unit,
    onOpenGuide: () -> Unit,
    onPickHtml: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AppGroupedSection(title = "在线导入") {
            AppNavigationRow(
                title = "打开厦大教务",
                description = "登录后保存课表，内容仅在本机解析",
                icon = Icons.Filled.CloudDownload,
                onClick = onOpenWebView,
                modifier = Modifier.testTag("import_open_webview"),
            )
            AppListDivider()
            AppNavigationRow(
                title = "查看导入教程",
                description = "了解登录与保存步骤",
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                onClick = onOpenGuide,
                modifier = Modifier.testTag("import_open_guide"),
            )
        }

        AppGroupedSection(title = "本地导入") {
            AppNavigationRow(
                title = "选择 HTML 文件",
                description = "适合离线导入与开发测试",
                icon = Icons.Filled.Description,
                onClick = onPickHtml,
                modifier = Modifier.testTag("import_pick_html"),
            )
        }

        Text(
            text = "本应用不保存账号密码，也不会上传导入内容；课表解析在本机完成。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ImportStartDatePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Long?) -> Unit,
    errorMessage: String? = null,
    initialDisplayedMonthMillis: Long? = null,
) {
    ProvideSimplifiedChineseDatePickerLocale {
        val uriHandler = LocalUriHandler.current
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = DEFAULT_IMPORT_START_DATE_MILLIS,
            initialDisplayedMonthMillis = initialDisplayedMonthMillis ?: DEFAULT_IMPORT_START_DATE_MILLIS,
        )
        DatePickerDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(datePickerState.selectedDateMillis) },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text("确认导入") }
            },
            dismissButton = { TextButton(onClick = onDismissRequest) { Text("取消") } },
        ) {
            ImportStartDatePickerBody(
                datePickerState = datePickerState,
                errorMessage = errorMessage,
                onOpenAcademicCalendar = { uriHandler.openUri(XMU_ACADEMIC_CALENDAR_URL) },
            )
        }
    }
}

@Composable
internal fun ProvideSimplifiedChineseDatePickerLocale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val simplifiedChineseConfiguration = remember(configuration) {
        Configuration(configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
    }
    val simplifiedChineseContext = remember(context, simplifiedChineseConfiguration) {
        context.createConfigurationContext(simplifiedChineseConfiguration)
    }

    CompositionLocalProvider(
        LocalContext provides simplifiedChineseContext,
        LocalConfiguration provides simplifiedChineseConfiguration,
    ) { content() }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ImportStartDatePickerBody(
    datePickerState: androidx.compose.material3.DatePickerState,
    errorMessage: String? = null,
    onOpenAcademicCalendar: () -> Unit,
) {
    Column {
        Text(
            "请选择第一教学周的星期一作为开学日期",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        TextButton(
            onClick = onOpenAcademicCalendar,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text("查看厦大校历（2026–2027 学年）")
        }
        errorMessage?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        DatePicker(state = datePickerState, showModeToggle = false)
    }
}

/** 读取 SAF 选中的 HTML 文件为字符串；失败返回 null。 */
private fun readHtmlFromUri(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}






