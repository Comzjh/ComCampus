package com.xmu.course.ui.update

import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.xmu.course.data.update.UpdateCheckResult
import com.xmu.course.data.update.VersionComparator
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Text(
                buildString {
                    append("当前版本：${update.currentVersion}\n最新版本：${update.latestVersion}")
                    if (downloading) append("\n正在从 GitHub 下载并校验安装包…")
                    if (downloadedApk != null) append("\n安装包已准备好，继续后将由 Android 安装器确认安装。")
                    if (downloadError) append("\n下载或完整性校验失败，请从 GitHub Release 页面下载。")
                },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        downloadedApk != null -> runCatching {
                            launchUpdateInstaller(context, downloadedApk, update.latestVersion)
                        }
                        canDownload -> onDownload()
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
