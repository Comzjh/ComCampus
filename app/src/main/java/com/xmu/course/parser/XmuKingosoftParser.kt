package com.xmu.course.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 厦门大学金智教务“学生课程表”解析器。
 *
 * 以真实手机端页面（wdkbapp #/xskcb 导出 MHT）为唯一数据源分析得出：
 * - 网格表 `#jsTbl_01`
 * - 课程单元格带 `jc`(开始节次) / `xq`(星期1-7,7=周日) / `rowspan`(持续节数)
 *   兜底：`jcxq`="节-星期"（如 1-2 = 第1节星期二）
 * - 每条排课为 `div.arrage`，子 div 顺序：
 *   [0]=周次文本, [1]=课程名, [2]=教师, [3]=教室(可能缺失，手机端常见 3 子节点)
 *
 * 解析以 .arrage 为主驱动，任何局部异常降级为 warning，整体不抛异常。
 */
class XmuKingosoftParser {

    fun parse(html: String): XmuParseResult {
        val warnings = mutableListOf<String>()
        if (html.isBlank()) {
            return XmuParseResult("", "", emptyList(), listOf("输入 HTML 为空"))
        }
        val document = runCatching { Jsoup.parse(html) }
            .getOrElse { return XmuParseResult("", "", emptyList(), listOf("HTML 解析失败: ${it.message}")) }

        val (semesterCode, semesterName) = parseSemester(document.selectFirst("#myXnxqSelect"))
        val grid = document.selectFirst("#jsTbl_01")
            ?: return XmuParseResult(semesterCode, semesterName, emptyList(), listOf("未找到课表网格 #jsTbl_01"))

        val courses = mutableListOf<ParsedCourse>()
        val arrages = grid.select("div.arrage")
        arrages.forEachIndexed { index, arrage ->
            parseArrage(arrage, index, warnings)?.let { courses.add(it) }
        }

        return XmuParseResult(semesterCode, semesterName, courses, warnings)
    }

    /** 学期下拉框：优先选中项，其次第一个 option。 */
    private fun parseSemester(select: Element?): Pair<String, String> {
        if (select == null) return "" to ""
        val selected = select.selectFirst("option[selected]") ?: select.selectFirst("option")
        return (selected?.attr("value") ?: "") to (selected?.text() ?: "")
    }

    /** 解析单个 .arrage 排课记录。 */
    private fun parseArrage(
        arrage: Element,
        index: Int,
        warnings: MutableList<String>,
    ): ParsedCourse? {
        val cell = arrage.closest("td") ?: run {
            warnings += "arrage[$index] 无父级 td，已跳过"
            return null
        }

        // 优先 jc/xq 属性，缺失时解析 jcxq="节-星期"。
        val startSection = cell.attr("jc").toIntOrNull()
            ?: cell.attr("jcxq").substringBefore('-').toIntOrNull()
        val dayOfWeek = cell.attr("xq").toIntOrNull()
            ?: cell.attr("jcxq").substringAfter('-').toIntOrNull()
        val duration = cell.attr("rowspan").ifBlank { "1" }.toIntOrNull() ?: 1

        if (startSection == null || dayOfWeek == null) {
            warnings += "arrage[$index] 缺少节次/星期属性，已跳过"
            return null
        }
        if (dayOfWeek !in 1..7 || startSection <= 0 || duration <= 0) {
            warnings += "arrage[$index] 节次/星期非法（day=$dayOfWeek, section=$startSection），已跳过"
            return null
        }

        val fields = arrage.select("> div").map { it.text().trim() }
        val weeksText = fields.getOrNull(0).orEmpty()
        val name = fields.getOrNull(1).orEmpty()
        val teacher = fields.getOrNull(2).orEmpty()
        val location = fields.getOrNull(3).orEmpty() // 手机端可能无教室行

        val weeks = WeekPatternParser.parse(weeksText)
        if (name.isEmpty() || weeks.isEmpty()) {
            warnings += "arrage[$index] 字段缺失（weeksText='$weeksText', name='$name'），已跳过"
            return null
        }

        return ParsedCourse(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            duration = duration,
            weeks = weeks,
        )
    }
}
