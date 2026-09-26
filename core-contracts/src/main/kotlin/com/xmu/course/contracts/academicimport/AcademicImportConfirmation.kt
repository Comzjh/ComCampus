package com.xmu.course.contracts.academicimport

/** 用户确认边界：只有确认后的记录才允许交给后续 Feature adapter。 */
sealed interface AcademicImportDecision {
    data class Confirmed(
        val courses: List<ConfirmedAcademicCourse>,
    ) : AcademicImportDecision

    data object Cancelled : AcademicImportDecision
}

/** 确认阶段仍保留原始学分文本，数值转换由后续明确的 Feature adapter 负责。 */
data class ConfirmedAcademicCourse(
    val name: String,
    val creditText: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(creditText.isNotBlank()) { "creditText must not be blank" }
    }
}
