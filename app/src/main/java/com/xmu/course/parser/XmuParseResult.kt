package com.xmu.course.parser

/**
 * 解析层输出的课程时段（不含 id/semesterId，由 Phase 4 导入时分配）。
 */
data class ParsedCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val duration: Int,
    val weeks: Set<Int>,
)

/**
 * 一次完整解析的结果。
 *
 * [warnings] 记录可恢复的解析问题（如某个 arrage 节点字段缺失），用于诊断而非阻断导入。
 */
data class XmuParseResult(
    val semesterCode: String,
    val semesterName: String,
    val courses: List<ParsedCourse>,
    val warnings: List<String> = emptyList(),
)
