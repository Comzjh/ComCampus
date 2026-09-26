package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicImportDecision
import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse
import com.xmu.course.data.academicimport.parser.AcademicImportCandidateProvenance

/**
 * 将“用户在 review 页面选择的条目”转换为 confirmation decision。
 * 调用方必须先完成用户选择；此 mapper 不自行决定哪些条目被选择。
 */
class AcademicImportDecisionMapper {
    fun map(selectedItems: List<AcademicImportReviewItem>): AcademicImportDecisionMapping {
        val confirmed = mutableListOf<ConfirmedAcademicCourse>()
        val rejected = mutableListOf<AcademicImportRejectedItem>()

        selectedItems.forEach { item ->
            val name = item.name?.trim()
            val creditText = item.creditText?.trim()
            val rejection = when {
                name.isNullOrBlank() -> AcademicImportRejectionReason.NAME_MISSING
                creditText.isNullOrBlank() -> AcademicImportRejectionReason.CREDIT_MISSING
                item.kind != AcademicRecordKind.COMPLETED -> AcademicImportRejectionReason.NOT_COMPLETED
                item.provenance != AcademicImportCandidateProvenance.COMPLETED_DETAIL ->
                    AcademicImportRejectionReason.PROVENANCE_NOT_CONFIRMABLE
                else -> null
            }
            if (rejection != null) {
                rejected += AcademicImportRejectedItem(item = item, reason = rejection)
            } else {
                confirmed += ConfirmedAcademicCourse(name = name!!, creditText = creditText!!)
            }
        }

        return AcademicImportDecisionMapping(
            decision = if (confirmed.isEmpty()) {
                AcademicImportDecision.Cancelled
            } else {
                AcademicImportDecision.Confirmed(confirmed)
            },
            rejectedItems = rejected,
        )
    }
}

data class AcademicImportDecisionMapping(
    val decision: AcademicImportDecision,
    val rejectedItems: List<AcademicImportRejectedItem>,
)

data class AcademicImportRejectedItem(
    val item: AcademicImportReviewItem,
    val reason: AcademicImportRejectionReason,
)

enum class AcademicImportRejectionReason {
    NAME_MISSING,
    CREDIT_MISSING,
    NOT_COMPLETED,
    PROVENANCE_NOT_CONFIRMABLE,
}
