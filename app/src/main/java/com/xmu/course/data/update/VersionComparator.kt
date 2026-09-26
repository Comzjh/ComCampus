package com.xmu.course.data.update

/**
 * 版本比较器：先比较数字核心（0.7.1 < 0.8.0 < 1.0.0），核心相同时按 prerelease
 * 排序：alpha < beta < rc < stable；同标签按尾随数字比较（rc < rc1 < rc2）。
 * 未知标签与 beta 同级、按字典序确定性排列，但始终低于 stable。
 * build metadata（+...）不参与升级判定（0.8.0+1 == 0.8.0+2）。
 * 无法解析的输入返回 null（视为 Unknown，不触发更新提示）。
 */
object VersionComparator {
    fun compare(left: String, right: String): Int? {
        val leftParts = parse(left) ?: return null
        val rightParts = parse(right) ?: return null
        for (index in 0 until maxOf(leftParts.core.size, rightParts.core.size)) {
            val leftPart = leftParts.core.getOrElse(index) { 0 }
            val rightPart = rightParts.core.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return comparePrerelease(leftParts.prerelease, rightParts.prerelease)
    }

    fun isNewer(current: String, candidate: String): Boolean = compare(candidate, current)?.let { it > 0 } == true

    private data class ParsedVersion(val core: List<Int>, val prerelease: Prerelease?)

    /** null 表示 stable（无 prerelease 后缀）。 */
    private data class Prerelease(val rank: Int, val label: String, val number: Int)

    private fun comparePrerelease(left: Prerelease?, right: Prerelease?): Int {
        if (left == null && right == null) return 0
        if (left == null) return 1  // stable > prerelease
        if (right == null) return -1
        if (left.rank != right.rank) return left.rank.compareTo(right.rank)
        if (left.label != right.label) return left.label.compareTo(right.label)
        return left.number.compareTo(right.number)
    }

    private val knownRanks = mapOf("alpha" to 0, "beta" to 1, "rc" to 2)
    private const val unknownRank = 1  // 未知标签与 beta 同级，低于 rc/stable

    private fun parse(raw: String): ParsedVersion? {
        val normalized = raw.trim().removePrefix("v").removePrefix("V")
        if (normalized.isEmpty()) return null
        val withoutMetadata = normalized.substringBefore('+')
        val numericPart = withoutMetadata.substringBefore('-')
        val prerelease = withoutMetadata.substringAfter('-', "").takeIf { it.isNotEmpty() }?.let(::parsePrerelease)
        val parts = numericPart.split('.')
        if (parts.isEmpty() || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        return ParsedVersion(parts.map { it.toIntOrNull() ?: return null }, prerelease)
    }

    private fun parsePrerelease(raw: String): Prerelease {
        val label = raw.takeWhile { it.isLetter() }.lowercase()
        val number = raw.dropWhile { it.isLetter() }.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val rank = knownRanks[label] ?: unknownRank
        return Prerelease(rank, label, number)
    }
}
