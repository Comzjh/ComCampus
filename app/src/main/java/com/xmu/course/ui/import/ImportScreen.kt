package com.xmu.course.ui.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

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
    viewModel: ImportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

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

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("导入课表") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(
                headlineContent = { Text("方式一：从教务系统导入") },
                supportingContent = { Text("登录厦大教务后点“保存课表”，全程本地处理") },
            )
            Button(onClick = onOpenWebView, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("打开厦大教务")
            }

            ListItem(
                headlineContent = { Text("方式二：选择本地 HTML 文件") },
                supportingContent = { Text("适合离线导入与开发测试") },
            )
            Button(onClick = { filePicker.launch(arrayOf("text/html")) }, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Filled.Description, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("选择 HTML 文件")
            }

            Text(
                text = "隐私说明：本应用不保存账号密码，不上传任何数据，所有解析均在本地完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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






