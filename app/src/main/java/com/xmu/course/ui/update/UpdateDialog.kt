package com.xmu.course.ui.update

import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import androidx.compose.material3.AlertDialog
import com.xmu.course.ui.components.AppButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xmu.course.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xmu.course.data.update.UpdateCheckResult
import com.xmu.course.data.update.VersionComparator
import com.xmu.course.ui.theme.AppShapes
import java.io.File

@Composable
fun UpdateDialog(
    update: UpdateCheckResult.Available,
    onDismiss: () -> Unit,
    downloading: Boolean,
    downloadedApkPath: String?,
    downloadError: Boolean,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val downloadedApk = downloadedApkPath?.let(::File)
    val canDownload = update.apkUrl != null && update.apkDigest != null
    var installAfterDownload by remember(update.latestVersion) { mutableStateOf(false) }
    val summary = remember(update.releaseNotes) { summarizeReleaseNotes(update.releaseNotes) }
    LaunchedEffect(downloadedApkPath, downloadError, installAfterDownload) {
        if (installAfterDownload && (downloadedApk != null || downloadError)) {
            installAfterDownload = false
            if (downloadedApk != null) runCatching {
                launchUpdateInstaller(context, downloadedApk, update.latestVersion)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.Section,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        title = { Text("发现新版本") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("当前版本：${update.currentVersion}\n最新版本：${update.latestVersion}")
                Text("更新内容", style = MaterialTheme.typography.titleSmall)
                Text(summary)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))) }
                }) { Text("GitHub 详情") }
                val status = buildString {
                    if (downloading) append("正在下载更新…")
                    if (downloadedApk != null) append("安装包已准备好，继续安装。")
                    if (downloadError) append("下载或校验失败，可重试或前往 GitHub 下载。")
                }
                if (status.isNotEmpty()) Text(status)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        downloadedApk != null -> runCatching {
                            launchUpdateInstaller(context, downloadedApk, update.latestVersion)
                        }
                        canDownload -> {
                            installAfterDownload = true
                            onDownload()
                        }
                        else -> runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
                        }
                    }
                },
                enabled = !downloading,
            ) {
                Text(
                    when {
                        downloading -> "正在下载…"
                        downloadedApk != null -> "安装更新"
                        canDownload -> "下载并安装"
                        else -> "前往 GitHub"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
    )
}

/** Keep the prompt short; the linked release page retains the full Markdown notes. */
internal fun summarizeReleaseNotes(notes: String): String {
    val summary = notes.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it != "---" }
        .map {
            it.replace(Regex("^([-*+]\\s+|\\d+[.)]\\s+)"), "")
                .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                .replace("**", "").replace("`", "")
        }
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString("\n") { "• $it" }
    if (summary.isBlank()) return "暂无更新说明，可查看 GitHub 详情。"
    return if (summary.length > 180) summary.take(179).trimEnd() + "…" else summary
}

private fun launchUpdateInstaller(context: android.content.Context, apk: File, expectedVersion: String) {
    require(apk.isFile && apk.parentFile == File(context.cacheDir, "updates"))
    val packageInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        ?: error("无法读取更新包信息")
    require(packageInfo.packageName == context.packageName) { "更新包与当前应用标识不一致" }
    require(VersionComparator.compare(packageInfo.versionName.orEmpty(), expectedVersion) == 0) {
        "更新包版本与发布版本不一致"
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return
    }

    val apkUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk,
    )
    context.startActivity(
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
