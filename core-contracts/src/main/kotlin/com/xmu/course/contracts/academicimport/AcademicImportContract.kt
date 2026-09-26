package com.xmu.course.contracts.academicimport

/**
 * 供 Grades 等 Feature 消费的课程记录。该模型不携带文件、解析器或 Provider 信息。
 */
data class AcademicImportedCourse(
    val courseName: String,
    val credit: Double,
    val semester: String? = null,
    val category: String? = null,
) {
    init {
        require(courseName.isNotBlank()) { "courseName must not be blank" }
        require(credit.isFinite() && credit > 0.0) { "credit must be a positive finite number" }
    }
}

/** 解析器对报告中的课程行作出的业务级分类。 */
enum class AcademicRecordKind {
    COMPLETED,
    PLANNED,
    UNKNOWN,
}

/** 需要用户确认的候选课程，允许字段为空以表达“无法猜测”。 */
data class AcademicImportCandidate(
    val courseName: String?,
    val credit: Double?,
    val semester: String? = null,
    val category: String? = null,
    val recordKind: AcademicRecordKind = AcademicRecordKind.UNKNOWN,
)

data class AcademicImportIssue(
    val reason: AcademicImportIssueReason,
    val candidateIndex: Int? = null,
)

enum class AcademicImportIssueReason {
    UNSUPPORTED_DOCUMENT,
    NO_COURSES_FOUND,
    COURSE_NAME_MISSING,
    CREDIT_MISSING,
    AMBIGUOUS_RECORD,
    INVALID_CREDIT,
}

/** Academic Import 的最小稳定输出，不暴露文件、网络或存储实现细节。 */
sealed interface AcademicImportResult {
    data class Ready(
        val courses: List<AcademicImportedCourse>,
        val warnings: List<AcademicImportIssue> = emptyList(),
    ) : AcademicImportResult

    data class NeedsReview(
        val candidates: List<AcademicImportCandidate>,
        val issues: List<AcademicImportIssue>,
    ) : AcademicImportResult

    data class Failed(
        val reason: AcademicImportIssueReason,
    ) : AcademicImportResult
}
