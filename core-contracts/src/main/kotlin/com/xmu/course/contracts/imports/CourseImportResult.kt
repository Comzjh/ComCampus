package com.xmu.course.contracts.imports

/** Feature 可理解的课程导入结果，不携带存储标识或 Provider 数据。 */
sealed interface CourseImportResult {
    data class Success(val importedCount: Int) : CourseImportResult

    data object NoChanges : CourseImportResult

    data object Unavailable : CourseImportResult

    data class Failed(val reason: ImportFailureReason) : CourseImportResult
}

enum class ImportFailureReason {
    UNKNOWN,
    SESSION_REQUIRED,
    PARSE_FAILED,
}
