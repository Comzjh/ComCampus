package com.xmu.course.data.academicimport

import java.io.InputStream

/** PDF 文本读取的 data-side seam，未来可替换成 OCR 实现。 */
interface PdfTextExtractor {
    fun extract(input: InputStream): ExtractedAcademicDocument
}

data class ExtractedAcademicDocument(
    val pages: List<ExtractedAcademicPage>,
) {
    val pageCount: Int get() = pages.size
}

data class ExtractedAcademicPage(
    val pageNumber: Int,
    val textRuns: List<ExtractedTextRun>,
)

data class ExtractedTextRun(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
