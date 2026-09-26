package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.parser.AcademicImportParseResult
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence
import com.xmu.course.data.academicimport.parser.AcademicParseFailureReason
import com.xmu.course.data.academicimport.parser.AcademicImportCandidateProvenance
import com.xmu.course.data.academicimport.parser.AcademicRecordCandidate
import com.xmu.course.data.academicimport.parser.AcademicReviewReason as ParserReviewReason

/** UI/后续确认流程可消费的 review 数据；仍不表达最终成绩或 GPA 语义。 */
data class AcademicImportReviewModel(
    val status: AcademicImportReviewStatus,
    val items: List<AcademicImportReviewItem>,
    val reasons: List<AcademicImportReviewReason> = emptyList(),
    val failureReason: AcademicParseFailureReason? = null,
)

data class AcademicImportReviewItem(
    val name: String?,
    val creditText: String?,
    val kind: AcademicRecordKind,
    val confidence: AcademicParseConfidence,
    val pageNumber: Int?,
    val provenance: AcademicImportCandidateProvenance = AcademicImportCandidateProvenance.UNKNOWN_SECTION,
    val reasons: List<AcademicImportReviewReason> = emptyList(),
)

enum class AcademicImportReviewStatus {
    READY,
    NEEDS_REVIEW,
    FAILED,
}

fun AcademicImportParseResult.toReviewModel(): AcademicImportReviewModel = when (this) {
    is AcademicImportParseResult.Parsed -> AcademicImportReviewModel(
        status = AcademicImportReviewStatus.READY,
        items = records.map(AcademicRecordCandidate::toReviewItem),
        reasons = warnings.mapNotNull(ParserReviewReason::toReviewReason).distinct(),
    )

    is AcademicImportParseResult.NeedsReview -> AcademicImportReviewModel(
        status = AcademicImportReviewStatus.NEEDS_REVIEW,
        items = records.map(AcademicRecordCandidate::toReviewItem),
        reasons = reasons.mapNotNull(ParserReviewReason::toReviewReason).distinct(),
    )

    is AcademicImportParseResult.Failed -> AcademicImportReviewModel(
        status = AcademicImportReviewStatus.FAILED,
        items = emptyList(),
        failureReason = reason,
    )
}

private fun AcademicRecordCandidate.toReviewItem() = AcademicImportReviewItem(
    name = name,
    creditText = creditText,
    kind = kind,
    confidence = confidence,
    pageNumber = pageNumber,
    provenance = provenance,
    reasons = buildList {
        if (name.isNullOrBlank()) add(AcademicImportReviewReason.MISSING_NAME)
        if (creditText.isNullOrBlank()) add(AcademicImportReviewReason.MISSING_CREDIT)
        if (kind == AcademicRecordKind.UNKNOWN) add(AcademicImportReviewReason.UNKNOWN_RECORD_KIND)
        if (confidence != AcademicParseConfidence.HIGH) add(AcademicImportReviewReason.UNCERTAIN_LAYOUT)
        if (provenance == AcademicImportCandidateProvenance.UNKNOWN_SECTION) {
            add(AcademicImportReviewReason.UNCERTAIN_SOURCE)
        }
    },
)

private fun ParserReviewReason.toReviewReason(): AcademicImportReviewReason? = when (this) {
    ParserReviewReason.NO_RELIABLE_COURSE_ROWS -> AcademicImportReviewReason.UNCERTAIN_LAYOUT
    ParserReviewReason.COURSE_NAME_MISSING -> AcademicImportReviewReason.MISSING_NAME
    ParserReviewReason.CREDIT_MISSING -> AcademicImportReviewReason.MISSING_CREDIT
    ParserReviewReason.AMBIGUOUS_COURSE_ROW -> AcademicImportReviewReason.UNCERTAIN_LAYOUT
    ParserReviewReason.PLANNED_COURSE_PRESENT -> AcademicImportReviewReason.PLANNED_RECORD_PRESENT
    ParserReviewReason.UNCERTAIN_SOURCE -> AcademicImportReviewReason.UNCERTAIN_SOURCE
}
