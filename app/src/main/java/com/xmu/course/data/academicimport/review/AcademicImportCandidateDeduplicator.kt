package com.xmu.course.data.academicimport.review

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import java.text.Normalizer

/**
 * 用于 review 层的候选重复风险标记。
 *
 * 该类不删除、不合并候选，也不试图推断课程 ID 或重修关系。输入中的每一条记录
 * 都会出现在 [DeduplicationResult.uniqueItems] 或 [DeduplicationResult.possibleDuplicates]
 * 中，供上层交给用户确认。
 */
class AcademicImportCandidateDeduplicator {
    fun deduplicate(items: List<AcademicImportReviewItem>): DeduplicationResult {
        val grouped = linkedMapOf<AcademicImportCandidateKey, MutableList<AcademicImportIndexedItem>>()

        items.forEachIndexed { index, item ->
            item.toCandidateKey()?.let { key ->
                grouped.getOrPut(key) { mutableListOf() }
                    .add(AcademicImportIndexedItem(index = index, item = item))
            }
        }

        val duplicateIndexes = mutableSetOf<Int>()
        val possibleDuplicates = grouped
            .filterValues { it.size > 1 }
            .map { (key, group) ->
                duplicateIndexes += group.map(AcademicImportIndexedItem::index)
                AcademicImportPossibleDuplicate(
                    key = key,
                    items = group.map(AcademicImportIndexedItem::item).map { item ->
                        item.copy(
                            reasons = (item.reasons + AcademicImportReviewReason.AMBIGUOUS_DUPLICATE).distinct(),
                        )
                    },
                )
            }

        return DeduplicationResult(
            uniqueItems = items.filterIndexed { index, _ -> index !in duplicateIndexes },
            possibleDuplicates = possibleDuplicates,
        )
    }
}

/** 不是课程 ID，只是 review 阶段用于标记明显相同候选的比较键。 */
data class AcademicImportCandidateKey(
    val nameNormalized: String,
    val creditTextNormalized: String?,
    val kind: AcademicRecordKind,
)

data class DeduplicationResult(
    /** 没有找到相同键的候选；重复风险项保留在 [possibleDuplicates] 中。 */
    val uniqueItems: List<AcademicImportReviewItem>,
    /** 可能重复的候选分组；不会自动合并或丢弃其中任何一条。 */
    val possibleDuplicates: List<AcademicImportPossibleDuplicate>,
)

data class AcademicImportPossibleDuplicate(
    val key: AcademicImportCandidateKey,
    val items: List<AcademicImportReviewItem>,
)

private data class AcademicImportIndexedItem(
    val index: Int,
    val item: AcademicImportReviewItem,
)

private fun AcademicImportReviewItem.toCandidateKey(): AcademicImportCandidateKey? {
    val nameNormalized = name.normalizeForCandidateKey() ?: return null
    val creditTextNormalized = creditText.normalizeForCandidateKey() ?: return null
    return AcademicImportCandidateKey(
        nameNormalized = nameNormalized,
        creditTextNormalized = creditTextNormalized,
        kind = kind,
    )
}

private fun String?.normalizeForCandidateKey(): String? {
    val normalized = this
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
    return normalized
}
