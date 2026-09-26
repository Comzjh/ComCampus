package com.xmu.course.adapter.jw

import android.net.Uri
import com.xmu.course.data.academicimport.AcademicImportFileSource

/**
 * JW 学业文档接缝：把用户主动下载产生的一次性 PDF 事件单向转换为
 * AcademicImportFileSource；不保存 PDF/URI，不自动触发下载，不后台访问 JW。
 */
interface JwDocumentProvider {
    /** 用户主动打开官方学业完成查询页面（MANUAL_ONLY）。 */
    fun openDocumentPage()

    /**
     * 将用户主动下载产生的 PDF 事件转换为一次性输入源；
     * 非 PDF MIME 或非法 scheme 时返回 null，不抛出、不静默保存。
     */
    fun createOneShotPdfSource(event: JwAcademicPdfDownload): AcademicImportFileSource?
}

/** 用户主动下载产生的 JW 学业 PDF 事件；URI 只在本次调用内有效，不持久化。 */
data class JwAcademicPdfDownload(
    val uri: Uri,
    val mimeType: String?,
)