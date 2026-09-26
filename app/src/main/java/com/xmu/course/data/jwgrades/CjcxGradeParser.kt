package com.xmu.course.data.jwgrades

import java.math.BigDecimal
import org.json.JSONObject

/**
 * cjcx `datas.xscjcx.rows` → 本地成绩条目。
 *
 * 设计约束：
 * - 白名单取字段：仅读取官方展示语义所需字段，绝不读取/保留 XH、XS 等身份列；
 * - 官方原文优先：成绩/绩点/学分保留文本形态，不做 GPA、等级制换算与聚合改写；
 * - fail-closed：整单任一行缺必填或类型异常即整单拒绝（宁缺勿错）；
 * - 拒绝理由只含结构信息（行号/字段名），不回显任何成绩内容。
 */
object CjcxGradeParser {

    sealed interface ParseResult {
        data class Success(
            val entries: List<JwGradeEntry>,
            val totalCreditsText: String,
        ) : ParseResult

        /** reason 仅为结构描述，可安全展示/落日志。 */
        data class Rejected(val reason: String) : ParseResult
    }

    /** 解析 [com.xmu.course.adapter.jw.JwEnvelopeResult.Ok] 的整封 payload。 */
    fun parseGrades(payload: JSONObject): ParseResult {
        val table = payload.optJSONObject("datas")?.optJSONObject("xscjcx")
            ?: return ParseResult.Rejected("缺少 datas.xscjcx 信封结构")
        val rows = table.optJSONArray("rows")
            ?: return ParseResult.Rejected("缺少 rows 数组")
        val declaredTotal = table.optInt("totalSize", rows.length())
        if (declaredTotal != rows.length()) {
            // pageSize 不足会导致静默截断：视为不完整数据整单拒绝，避免半份成绩单入库。
            return ParseResult.Rejected("分页不完整：totalSize=$declaredTotal rows=${rows.length()}")
        }
        if (rows.length() == 0) return ParseResult.Success(emptyList(), "0")

        val entries = ArrayList<JwGradeEntry>(rows.length())
        var creditSum = BigDecimal.ZERO
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index)
                ?: return ParseResult.Rejected("第 $index 行不是 JSON 对象")
            val entry = readRow(row, index)
                ?: return ParseResult.Rejected("第 $index 行字段缺失或非法")
            val credits = entry.creditsText.toBigDecimalOrNull()
                ?: return ParseResult.Rejected("第 $index 行学分不可解析")
            creditSum += credits
            entries += entry
        }
        return ParseResult.Success(entries, creditSum.toPlainString())
    }

    private fun readRow(row: JSONObject, index: Int): JwGradeEntry? {
        val semesterCode = requiredText(row, "XNXQDM") ?: return null
        val courseCode = requiredText(row, "KCH") ?: return null
        val courseName = requiredText(row, "KCM") ?: return null
        val credits = requiredText(row, "XF") ?: return null
        val grade = requiredText(row, "ZCJ") ?: return null
        // WID 缺失时用确定性合成键（学期+课程+行序），保证行标识稳定。
        val rowId = optionalText(row, "WID") ?: "$semesterCode#$courseCode#$index"
        return JwGradeEntry(
            rowId = rowId,
            semesterCode = semesterCode,
            semesterDisplay = optionalText(row, "XNXQDM_DISPLAY") ?: semesterCode,
            courseCode = courseCode,
            courseName = courseName,
            creditsText = credits,
            gradeText = grade,
            pointGradeText = optionalText(row, "XFJD"),
            courseNatureDisplay = optionalText(row, "KCXZDM_DISPLAY"),
            courseCategoryDisplay = optionalText(row, "KCLBDM_DISPLAY"),
            offeringUnitDisplay = optionalText(row, "KKDWDM_DISPLAY"),
            retakeCode = optionalText(row, "CXCKDM"),
        )
    }

    private fun requiredText(row: JSONObject, key: String): String? =
        textOf(row.opt(key))?.takeIf { it.isNotBlank() }

    private fun optionalText(row: JSONObject, key: String): String? =
        textOf(row.opt(key))?.takeIf { it.isNotBlank() }

    /** 官方数值/字符串统一为文本；数字避免科学计数法与浮点尾差。 */
    private fun textOf(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is String -> value
        is Number -> JSONObject.numberToString(value)
        is Boolean -> value.toString()
        else -> null
    }
}
