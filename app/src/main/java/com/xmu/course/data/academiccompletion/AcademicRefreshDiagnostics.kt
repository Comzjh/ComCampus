package com.xmu.course.data.academiccompletion

/** Fixed, non-user-derived categories written to a local support report. */
enum class AcademicRefreshDiagnosticStage {
    PLAN_SELECTION,
    SNAPSHOT_ASSEMBLY,
    SNAPSHOT_VALIDATION,
}

enum class AcademicRefreshDiagnosticCode {
    PLAN_NOT_FOUND,
    SOURCE_STRUCTURE_INVALID,
    SOURCE_VALIDATION_REJECTED,
    PLAN_CREDIT_TOTAL_MISMATCH,
}

/** Aggregate-only reconciliation details; deliberately contains no course or person identifiers. */
data class AcademicCreditReconciliationSummary(
    val coursePoolRowCount: Int,
    val coursePoolDistinctCodeCount: Int,
    val semesterCourseRowCount: Int,
    val semesterCourseDistinctCodeCount: Int,
    val inPlanCourseCount: Int,
    val outOfPlanCourseCount: Int,
    val inPlanMissingCreditCount: Int,
    val confirmedInPlanCreditCount: Int,
    val inPlanCreditsSum: String,
    val planLevelCredits: String,
    val reconciledWithPlanLevel: Boolean,
)

/** Safe diagnostic payload passed to the local archive writer. It never carries raw source text. */
data class AcademicRefreshDiagnostics(
    val stage: AcademicRefreshDiagnosticStage,
    val code: AcademicRefreshDiagnosticCode,
    val reconciliation: AcademicCreditReconciliationSummary? = null,
)
