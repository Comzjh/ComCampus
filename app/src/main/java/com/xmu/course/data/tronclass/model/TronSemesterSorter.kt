package com.xmu.course.data.tronclass.model

/**
 * 按学年和学期顺序排列缓存标签。
 *
 * 支持常见的 `2025-2026-1`、`2025-2026-2`、`2026-1` 及秋/春季标签；
 * 无法解析的标签不会导致页面崩溃，而是稳定地排在已解析标签之后。
 */
fun sortTronSemesterLabels(labels: Collection<String>): List<String> = labels
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .sortedWith(
        compareByDescending<String> { parseSemesterLabel(it) != null }
            .thenByDescending { parseSemesterLabel(it)?.academicYear ?: Int.MIN_VALUE }
            .thenByDescending { parseSemesterLabel(it)?.term ?: Int.MIN_VALUE }
            .thenBy { it },
    )

private data class SemesterKey(val academicYear: Int, val term: Int)

private fun parseSemesterLabel(label: String): SemesterKey? {
    val compact = label.replace("学年", "").replace("学期", "").trim()
    Regex("^(\\d{4})([12])$").matchEntire(compact)?.let { match ->
        return SemesterKey(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    val years = Regex("\\d{4}").findAll(compact).map { it.value.toInt() }.toList()
    // 学年区间的结束年份更能表示其时间位置：2026-2027 应优先于 2026-1 展示。
    val academicYear = years.lastOrNull() ?: return null
    val term = when {
        compact.contains("秋") || compact.contains("上") -> 1
        compact.contains("春") || compact.contains("下") -> 2
        else -> Regex("(?<!\\d)([12])(?=\\D|$)")
            .findAll(compact)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    } ?: return null
    return SemesterKey(academicYear, term)
}
