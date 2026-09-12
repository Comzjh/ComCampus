package com.xmu.course.ui.support

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xmu.course.R

private const val PROJECT_URL = "https://github.com/your-name/XMU-Course"

private data class SupportImage(
    @DrawableRes val resourceId: Int,
    val title: String,
    val fileName: String,
)

private val SUPPORT_IMAGES = listOf(
    SupportImage(R.drawable.alipay_qr, "支付宝", "xmu-course-alipay"),
    SupportImage(R.drawable.wechat_qr, "微信支付", "xmu-course-wechat"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var preview by remember { mutableStateOf<SupportImage?>(null) }
    var pendingSave by remember { mutableStateOf<SupportImage?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    fun save(image: SupportImage) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = image
        } else {
            val success = SupportImageSaver.saveToGallery(context, image.resourceId, image.fileName)
            feedback = if (success) "已保存到相册" else "保存失败"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val image = pendingSave
        pendingSave = null
        if (granted && image != null) {
            val success = SupportImageSaver.saveToGallery(context, image.resourceId, image.fileName)
            feedback = if (success) "已保存到相册" else "保存失败"
        }
    }

    LaunchedEffect(pendingSave) {
        pendingSave?.let { permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHost.showSnackbar(it)
            feedback = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("支持开发") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Text("支持 XMU Course", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "XMU Course 是一个面向厦大学生的开源课表项目。如果这个项目帮助到了你，欢迎支持开发。",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(SUPPORT_IMAGES) { image ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(image.title, style = MaterialTheme.typography.titleMedium)
                        Image(
                            painter = painterResource(image.resourceId),
                            contentDescription = "${image.title}收款码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(top = 8.dp)
                                .clickable { preview = image },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { preview = image }) { Text("点击放大") }
                            Button(onClick = { save(image) }) { Text("保存到相册") }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("项目主页", style = MaterialTheme.typography.titleMedium)
                        Text("开源地址：$PROJECT_URL", modifier = Modifier.padding(top = 6.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("打开项目主页") }
                    }
                }
            }
            item {
                Text(
                    "你的支持将用于：项目维护、功能开发、测试设备和开源服务费用。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    preview?.let { image ->
        Dialog(onDismissRequest = { preview = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(image.resourceId),
                        contentDescription = "${image.title}收款码大图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                    TextButton(onClick = { preview = null }) { Text("关闭") }
                }
            }
        }
    }
}
