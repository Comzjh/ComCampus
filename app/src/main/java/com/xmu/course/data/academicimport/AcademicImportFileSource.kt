package com.xmu.course.data.academicimport

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream

/** Academic Import 的一次性本地输入抽象；具体文件选择方式由上层稍后提供。 */
fun interface AcademicImportFileSource {
    suspend fun open(): InputStream
}

/** 将调用方已打开的输入流包装为不可重复打开的临时来源，不复制或保存内容。 */
class OneShotAcademicImportFileSource(
    private val input: InputStream,
) : AcademicImportFileSource {
    private var opened = false

    override suspend fun open(): InputStream {
        check(!opened) { "Academic Import file source can only be opened once" }
        opened = true
        return input
    }
}

/** 将系统文档 URI 适配为一次性输入流；URI 只存在于本次调用内，不做持久化。 */
class ContentResolverAcademicImportFileSource internal constructor(
    private val uri: Uri,
    private val openStream: (Uri) -> InputStream?,
) : AcademicImportFileSource {
    private var opened = false

    constructor(resolver: ContentResolver, uri: Uri) : this(uri, resolver::openInputStream)

    override suspend fun open(): InputStream {
        check(!opened) { "Academic Import document source can only be opened once" }
        opened = true
        return openStream(uri) ?: throw IOException("Selected academic report cannot be opened")
    }
}
