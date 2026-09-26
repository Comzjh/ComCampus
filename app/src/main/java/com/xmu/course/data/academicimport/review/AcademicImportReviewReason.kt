package com.xmu.course.data.academicimport.review

/**
 * Review 层对“不确定”的统一表达。
 *
 * 该模型不暴露 parser 或去重器的内部实现，多个原因可以同时存在。
 */
enum class AcademicImportReviewReason {
    MISSING_NAME,
    MISSING_CREDIT,
    AMBIGUOUS_DUPLICATE,
    UNKNOWN_RECORD_KIND,
    UNCERTAIN_LAYOUT,
    UNCERTAIN_SOURCE,
    PLANNED_RECORD_PRESENT,
}
