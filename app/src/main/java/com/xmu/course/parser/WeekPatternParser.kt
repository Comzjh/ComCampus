package com.xmu.course.parser

/**
 * 周次文本解析器。
 *
 * 支持金智教务出现的全部格式（实测样张）：
 * - 区间全周：`1-16周`、`3-16周`
 * - 区间单/双周：`1-15单周`、`2-16双周`
 * - 离散周：`1,4,7`
 * - 混合列表：`1-3单周,6,8-9,11-13单周,15-16周`
 *
 * 解析失败的分段直接跳过，绝不让异常格式导致崩溃。
 */
object WeekPatternParser {

    fun parse(text: String): Set<Int> {
        val result = sortedSetOf<Int>()
        val normalized = text.replace("，", ",").replace(" ", "").trim()
        if (normalized.isEmpty()) return result

        for (segment in normalized.split(',')) {
            if (segment.isBlank()) continue
            runCatching { parseSegment(segment, result) }
        }
        return result
    }

    /** 解析单段，如 `1-16周`、`2-16双周`、`6`、`8-9`。 */
    private fun parseSegment(segment: String, out: MutableSet<Int>) {
        // 判定单/双周标记（区间后缀，如 `1-15单周`）。
        var parity: Int? = null
        var core = segment
        when {
            segment.endsWith("单周") -> { parity = 1; core = segment.removeSuffix("单周") }
            segment.endsWith("双周") -> { parity = 2; core = segment.removeSuffix("双周") }
            segment.endsWith("周") -> core = segment.removeSuffix("周")
        }

        val rangeParts = core.split('-')
        val range = when (rangeParts.size) {
            1 -> {
                val week = rangeParts[0].toIntOrNull() ?: return
                week..week
            }
            2 -> {
                val start = rangeParts[0].toIntOrNull() ?: return
                val end = rangeParts[1].toIntOrNull() ?: return
                if (start > end) return
                start..end
            }
            else -> return
        }

        for (week in range) {
            val matchesParity = parity == null ||
                (parity == 1 && week % 2 == 1) ||
                (parity == 2 && week % 2 == 0)
            if (matchesParity && week > 0) out.add(week)
        }
    }
}
