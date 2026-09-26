package com.xmu.course.adapter.jw

import android.net.Uri

/**
 * 将用户触发的 JW 下载事件限制为可交给 Academic Import 的本地 PDF URI。
 *
 * 这里只做事件校验和一次性回调，不下载、复制或保存文件，也不访问 WebView 会话。
 */
fun JwAcademicPdfDownload.isValidAcademicPdfDownload(): Boolean =
    mimeType.isPdfMimeType() && uri.scheme in setOf("content", "file")

class JwAcademicReportDownloadHandler(
    private val onPdfDownloaded: (Uri) -> Unit,
) {
    fun handle(event: JwAcademicPdfDownload): Boolean {
        if (!event.isValidAcademicPdfDownload()) return false
        onPdfDownloaded(event.uri)
        return true
    }
}

private fun String?.isPdfMimeType(): Boolean =
    (this
        ?.substringBefore(';')
        ?.trim()
        ?.equals("application/pdf", ignoreCase = true)) == true