package com.xmu.course.data.academicimport.parser

import com.xmu.course.contracts.academicimport.AcademicRecordKind

/** Parser 层保留原始字段与不确定性，不直接生成 Grades 课程。 */
sealed interface AcademicImportParseResult {
    data class Parsed(
        val records: List<AcademicRecordCandidate>,
        val warnings: List<AcademicReviewReason> = emptyList(),
    ) : AcademicImportParseResult

    data class NeedsReview(
        val records: List<AcademicRecordCandidate>,
        val reasons: List<AcademicReviewReason>,
    ) : AcademicImportParseResult

    data class Failed(
        val reason: AcademicParseFailureReason,
    ) : AcademicImportParseResult
}

data class AcademicRecordCandidate(
    val name: String?,
    val creditText: String?,
    val category: String? = null,
    val kind: AcademicRecordKind = AcademicRecordKind.UNKNOWN,
    val confidence: AcademicParseConfidence = AcademicParseConfidence.LOW,
    val pageNumber: Int? = null,
    val provenance: AcademicImportCandidateProvenance = AcademicImportCandidateProvenance.UNKNOWN_SECTION,
)

enum class AcademicParseConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class AcademicReviewReason {
    NO_RELIABLE_COURSE_ROWS,
    COURSE_NAME_MISSING,
    CREDIT_MISSING,
    AMBIGUOUS_COURSE_ROW,
    PLANNED_COURSE_PRESENT,
    UNCERTAIN_SOURCE,
}

enum class AcademicParseFailureReason {
    NO_TEXT,
    UNSUPPORTED_LAYOUT,
}
