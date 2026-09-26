package com.xmu.course.data.academicimport

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream

/** 使用 PdfBox-Android 读取本地文本型 PDF；不保存输入内容。 */
class PdfBoxTextExtractor(
    context: Context,
) : PdfTextExtractor {
    private val applicationContext = context.applicationContext

    override fun extract(input: InputStream): ExtractedAcademicDocument {
        PDFBoxResourceLoader.init(applicationContext)
        PDDocument.load(input).use { document ->
            val pageBuffers = MutableList(document.numberOfPages) { ArrayList<ExtractedTextRun>() }
            val stripper = object : PDFTextStripper() {
                private var currentPageIndex = -1

                override fun processPage(page: com.tom_roush.pdfbox.pdmodel.PDPage?) {
                    // PDFTextStripper 按页顺序回调；不要依赖 PDPage 包装对象的引用身份。
                    currentPageIndex += 1
                    super.processPage(page)
                }

                override fun processTextPosition(text: TextPosition?) {
                    if (currentPageIndex >= 0 && text != null) {
                        val unicode = text.unicode?.trimEnd('\u0000').orEmpty()
                        if (unicode.isNotEmpty()) {
                            pageBuffers[currentPageIndex] += ExtractedTextRun(
                                text = unicode,
                                x = text.x,
                                y = text.y,
                                width = text.width,
                                height = text.height,
                            )
                        }
                    }
                }
            }
            // PDF 本身不保证阅读顺序；保留坐标，交由后续 parser 按报告布局重建行。
            stripper.sortByPosition = true
            stripper.getText(document)
            return ExtractedAcademicDocument(
                pages = pageBuffers.mapIndexed { index, runs ->
                    ExtractedAcademicPage(index + 1, runs.toList())
                },
            )
        }
    }
}
