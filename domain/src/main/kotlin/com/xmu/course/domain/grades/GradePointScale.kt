package com.xmu.course.domain.grades

/**
 * 一个明确的、由产品或学校规则配置提供的成绩换算规则。
 *
 * 不在领域层内置任何学校默认规则，避免把未经确认的规则冒充为厦大官方规则。
 * 百分制区间为闭区间；区间不能重叠，否则同一个成绩会得到多个绩点。
 */
class GradePointScale(
    letterPoints: Map<String, Double>,
    percentageBands: List<PercentageBand>,
) {
    private val normalizedLetterPoints: Map<String, Double>
    private val orderedPercentageBands: List<PercentageBand>

    init {
        val sortedPercentageBands = percentageBands.sortedByDescending { it.minimumInclusive }
        require(letterPoints.keys.all { it.isNotBlank() }) { "等级规则的键不能为空" }
        require(letterPoints.keys.map(::normalizeLetter).toSet().size == letterPoints.size) {
            "等级规则不能包含重复的大小写变体"
        }
        require(letterPoints.values.all { it.isFinite() && it >= 0.0 }) {
            "绩点必须是非负有限数值"
        }
        require(sortedPercentageBands.zipWithNext().none { (left, right) -> left.overlaps(right) }) {
            "百分制区间不能重叠"
        }

        normalizedLetterPoints = letterPoints.mapKeys { normalizeLetter(it.key) }
        orderedPercentageBands = sortedPercentageBands
    }

    /** 将手动输入的百分制或等级制成绩转换为绩点。 */
    fun pointsFor(input: GradeInput): Double = when (input) {
        is GradeInput.Percentage -> pointsForPercentage(input.value)
        is GradeInput.Letter -> normalizedLetterPoints[normalizeLetter(input.value)]
            ?: throw IllegalArgumentException("未配置等级成绩: ${input.value.trim()}")
    }

    private fun pointsForPercentage(value: Double): Double = orderedPercentageBands
        .firstOrNull { value in it.minimumInclusive..it.maximumInclusive }
        ?.points
        ?: throw IllegalArgumentException("百分制成绩不在配置区间内: $value")

    private fun normalizeLetter(value: String): String = value.trim().uppercase()
}

/**
 * GPA Sandbox 的通用等级示例规则。
 *
 * 这不是厦大官方规则，只为第一版手动模拟提供一个明确、可替换的默认输入规则。
 */
object GpaSandboxScales {
    val commonLetter: GradePointScale = GradePointScale(
        letterPoints = mapOf(
            "A" to 4.0,
            "A-" to 3.7,
            "B+" to 3.3,
            "B" to 3.0,
            "B-" to 2.7,
            "C+" to 2.3,
            "C" to 2.0,
            "C-" to 1.7,
            "D" to 1.0,
            "F" to 0.0,
        ),
        percentageBands = listOf(
            PercentageBand(90.0, 100.0, 4.0),
            PercentageBand(85.0, 89.0, 3.7),
            PercentageBand(82.0, 84.0, 3.3),
            PercentageBand(78.0, 81.0, 3.0),
            PercentageBand(75.0, 77.0, 2.7),
            PercentageBand(72.0, 74.0, 2.3),
            PercentageBand(68.0, 71.0, 2.0),
            PercentageBand(64.0, 67.0, 1.5),
            PercentageBand(60.0, 63.0, 1.0),
        ),
    )
}

/** 一个闭区间百分制规则，例如 85.0..89.0 对应 3.7。 */
data class PercentageBand(
    val minimumInclusive: Double,
    val maximumInclusive: Double,
    val points: Double,
) {
    init {
        require(minimumInclusive.isFinite() && maximumInclusive.isFinite()) {
            "百分制区间必须是有限数值"
        }
        require(minimumInclusive <= maximumInclusive) {
            "百分制区间下限不能大于上限"
        }
        require(points.isFinite() && points >= 0.0) {
            "绩点必须是非负有限数值"
        }
    }

    internal fun overlaps(other: PercentageBand): Boolean =
        minimumInclusive <= other.maximumInclusive && other.minimumInclusive <= maximumInclusive
}
