package com.xmu.course.di

import com.xmu.course.data.academicimport.AcademicImportFileSource
import com.xmu.course.data.academicimport.PdfTextExtractor
import com.xmu.course.data.academicimport.adapter.AcademicImportSandboxMapper
import com.xmu.course.data.academicimport.parser.AcademicImportParseResult
import com.xmu.course.data.academicimport.parser.AcademicReportParser
import com.xmu.course.data.academicimport.review.AcademicImportDecisionMapper
import com.xmu.course.data.academicimport.review.AcademicImportReviewModel
import com.xmu.course.data.academicimport.review.toReviewModel
import com.xmu.course.data.academicrecord.AcademicRecordImportSaver
import com.xmu.course.data.academicimport.xlsx.AcademicXlsxDocument
import com.xmu.course.data.academicimport.xlsx.AcademicXlsxRow
import com.xmu.course.data.academicimport.xlsx.TemporaryAcademicXlsxStore

/**
 * Academic Import 的独立组合能力。
 *
 * 该对象只把解析、审核、确认和 Sandbox 草稿适配能力组合起来；
 * 不负责文件选择、导航、Room、网络、认证或 GPA 计算。
 */
class AcademicImportDependencies(
    val pdfTextExtractor: PdfTextExtractor,
    val reportParser: AcademicReportParser,
    val reviewMapper: AcademicImportReviewMapper,
    val decisionMapper: AcademicImportDecisionMapper,
    val sandboxMapper: AcademicImportSandboxMapper,
    val academicRecordImportSaver: AcademicRecordImportSaver? = null,
    val temporaryXlsxStore: TemporaryAcademicXlsxStore? = null,
) {
    /** 在 Academic Import capability 内完成一次性读取与解析；输入流不会被保存。 */
    suspend fun review(source: AcademicImportFileSource): AcademicImportReviewModel {
        return source.open().use { input ->
            reviewMapper.map(reportParser.parse(pdfTextExtractor.extract(input)))
        }
    }

    /** PDF 仅作为一次性转换输入；UI 后续只读取临时 Excel 数据源。 */
    suspend fun previewFromXlsx(source: AcademicImportFileSource): AcademicImportXlsxPreview {
        val review = review(source)
        val store = requireNotNull(temporaryXlsxStore) { "Temporary Excel data source is not configured" }
        val rows = review.items.map { item ->
            AcademicXlsxRow(
                courseName = item.name,
                creditText = item.creditText,
                status = item.kind.name,
                sourcePage = item.pageNumber,
                confidence = item.confidence.name,
                reviewReason = item.reasons.takeIf(List<*>::isNotEmpty)?.joinToString("|") { it.name },
            )
        }
        return AcademicImportXlsxPreview(review.status, store.writeAndRead(rows))
    }
}

data class AcademicImportXlsxPreview(
    val status: com.xmu.course.data.academicimport.review.AcademicImportReviewStatus,
    val document: AcademicXlsxDocument,
)

/** 保持 parser 结果到 review 模型的转换点独立，供后续 UI 组合使用。 */
class AcademicImportReviewMapper {
    fun map(result: AcademicImportParseResult): AcademicImportReviewModel = result.toReviewModel()
}
