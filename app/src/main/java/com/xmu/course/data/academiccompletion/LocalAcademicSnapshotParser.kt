package com.xmu.course.data.academiccompletion

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

/** 导入解析结果：成功携带脱敏后的内部快照；失败携带全部拒绝原因。 */
sealed interface SnapshotImportResult {
    data class Success(val snapshot: AcademicCompletionSnapshot) : SnapshotImportResult
    data class Rejected(val reasons: List<String>) : SnapshotImportResult
}

private fun rejected(reasons: List<String>): SnapshotImportResult.Rejected =
    SnapshotImportResult.Rejected(reasons.ifEmpty { listOf("未知拒绝原因") })

/**
 * 来源 JSON 快照（学业完成查询导出）到内部模型的解析器。
 *
 * 职责边界（Phase 10.1 审查决议）：
 * - 外部来源格式只在这里被理解；内部持久化模型与本解析器解耦；
 * - fail-closed：已知必填字段缺失/非法/内部对账不一致 => 整单拒绝；
 *   方案级合计不一致仅允许显式调用方选择宽容导入；
 *   未知额外字段宽容忽略（required known field fail-closed, unknown extra tolerant）；
 * - 脱敏：来源中的学生身份字段（姓名/学号）一律不读入内部模型、不持久化；
 * - 无网络、无来源系统访问，只解析调用方提供的 JSON。
 */
object LocalAcademicSnapshotParser {

    private val DatePattern = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val TimestampPattern = Regex("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?")

    /** 稳定课程身份：单一学期快照内以课程代码为 exact identity；重复即拒绝（不做模糊匹配）。 */
    fun parse(
        rawJson: String,
        allowPlanLevelMismatch: Boolean = false,
    ): SnapshotImportResult {
        val root = try {
            JSONObject(rawJson)
        } catch (error: JSONException) {
            return rejected(listOf("文件不是合法的 JSON 对象"))
        }
        val reasons = mutableListOf<String>()

        val generatedAt = requiredText(root, "generated_at", "生成日期", reasons)
        if (generatedAt != null && !DatePattern.matches(generatedAt)) {
            reasons += "生成日期格式不正确"
        }
        val semesterRoot = requiredObject(root, "semester", reasons)
        val semesterLabel = semesterRoot?.let { requiredText(it, "jwapp_code", "学期代码", reasons) }
        val planRoot = requiredObject(root, "plan", reasons)
        val planName = planRoot?.let { requiredText(it, "name", "方案名称", reasons) }
        val requiredXf = planRoot?.let { requiredDecimal(it, "required_xf", "方案总学分", positive = true, reasons) }
        val earnedXf = planRoot?.let { requiredDecimal(it, "earned_xf_snapshot", "已获学分", positive = false, reasons) }
        val snapshotAt = planRoot?.let { requiredText(it, "snapshot_czsj", "快照时间", reasons) }
        if (snapshotAt != null && !TimestampPattern.matches(snapshotAt)) {
            reasons += "快照时间格式不正确"
        }
        val planLevelTotal = planRoot?.let {
            requiredDecimal(it, "this_semester_selected_xf_plan_level", "方案级本学期已选合计", positive = false, reasons)
        }
        val totalsRoot = requiredObject(root, "totals", reasons)
        val totalsSum = totalsRoot?.let {
            requiredDecimal(it, "in_plan_xf_sum", "方案内学分合计", positive = false, reasons)
        }
        val coursesArray = root.optJSONArray("courses")
        if (coursesArray == null) {
            reasons += "缺少课程列表（courses）"
        }

        val enrolled = mutableListOf<SourceCourse>()
        val seenCodes = mutableSetOf<String>()
        if (coursesArray != null) {
            for (index in 0 until coursesArray.length()) {
                val item = coursesArray.optJSONObject(index)
                if (item == null) {
                    reasons += "课程第 ${index + 1} 项不是对象"
                    continue
                }
                parseCourse(item, index + 1, reasons)?.let { course ->
                    if (!seenCodes.add(course.courseCode)) {
                        reasons += "课程代码重复：${course.courseCode}"
                    } else {
                        enrolled += course
                    }
                }
            }
        }

        val completed = mutableListOf<CompletedCourseOutsidePlan>()
        val seenCompletedCodes = mutableSetOf<String>()
        val completedArray = root.optJSONArray("faw_completed_courses")
        if (completedArray != null) {
            for (index in 0 until completedArray.length()) {
                val item = completedArray.optJSONObject(index)
                if (item == null) {
                    reasons += "方案外已结课第 ${index + 1} 项不是对象"
                    continue
                }
                parseCompletedOutsidePlan(item, index + 1, reasons)?.let { course ->
                    if (!seenCompletedCodes.add(course.courseCode)) {
                        reasons += "方案外已结课代码重复：${course.courseCode}"
                    } else {
                        completed += course
                    }
                }
            }
        }

        if (reasons.isNotEmpty()) {
            return rejected(reasons)
        }

        // —— 对账校验：课程明细必须与来源自身 totals 对齐；方案级合计可由刷新调用方显式降级为警告 ——
        val inPlanSumText = CreditsDecimal.sum(
            enrolled.filter { it.inPlan }.mapNotNull { it.creditsText },
        )
        if (planLevelTotal == null || totalsSum == null) {
            return rejected(listOf("对账所需字段不完整"))
        }
        val planLevel = BigDecimal(planLevelTotal)
        val totalsValue = BigDecimal(totalsSum)
        val inPlanSum = BigDecimal(inPlanSumText)
        if (inPlanSum.compareTo(planLevel) != 0 && !allowPlanLevelMismatch) {
            return rejected(
                listOf(
                    "方案内课程学分求和（${inPlanSumText}）与方案级本学期已选合计（${planLevelTotal}）不一致，" +
                        "快照可能过期或课程已变动，请重新抓取后再导入",
                ),
            )
        }
        if (inPlanSum.compareTo(totalsValue) != 0) {
            return rejected(
                listOf("方案内课程学分求和（${inPlanSumText}）与 totals 对账字段（${totalsSum}）不一致，快照数据自相矛盾"),
            )
        }

        // 全部校验通过后才构造内部模型（此刻才允许 store 落盘）。
        return SnapshotImportResult.Success(
            AcademicCompletionSnapshot(
                schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                semesterLabel = semesterLabel!!,
                plan = PlanSummary(
                    planName = planName!!,
                    requiredCreditsText = requiredXf!!,
                    earnedCreditsText = earnedXf!!,
                    sourceSnapshotAt = snapshotAt!!,
                    sourceThisSemesterTotalText = planLevelTotal,
                ),
                enrolledCourses = enrolled,
                completedCoursesOutsidePlan = completed,
                localOverrides = emptyMap(),
            ),
        )
    }

    private fun parseCourse(item: JSONObject, displayIndex: Int, reasons: MutableList<String>): SourceCourse? {
        val code = requiredText(item, "KCDM", "课程第 ${displayIndex} 项的课程代码", reasons) ?: return null
        val name = requiredText(item, "KCMC", "课程「${code}」的名称", reasons) ?: return null
        if (!item.has("in_plan") || item.isNull("in_plan")) {
            reasons += "课程「${name}」缺少方案内标记（in_plan）"
            return null
        }
        val inPlan = when (val raw = item.get("in_plan")) {
            is Boolean -> raw
            else -> {
                reasons += "课程「${name}」的方案内标记（in_plan）不是布尔值"
                return null
            }
        }
        val statusRaw = requiredText(item, "xf_status", "课程「${name}」的学分状态", reasons) ?: return null
        val status = when (statusRaw) {
            "confirmed" -> SourceXfStatus.CONFIRMED
            "需人工确认" -> SourceXfStatus.NEEDS_MANUAL
            else -> {
                reasons += "课程「${name}」存在无法识别的学分状态：${statusRaw}"
                return null
            }
        }
        val xfPresent = item.has("XF") && !item.isNull("XF")
        val creditsText = if (!xfPresent) {
            null
        } else {
            val normalized = CreditsDecimal.normalizePositive(item.get("XF").toString())
            if (normalized == null) {
                reasons += "课程「${name}」的学分不是合法正十进制数：${item.get("XF")}"
                return null
            }
            normalized
        }
        when {
            status == SourceXfStatus.CONFIRMED && creditsText == null -> {
                reasons += "课程「${name}」状态为已确认但缺少学分"
                return null
            }
            status == SourceXfStatus.NEEDS_MANUAL && creditsText != null -> {
                reasons += "课程「${name}」状态为需人工确认但来源已有学分（矛盾数据）"
                return null
            }
            status == SourceXfStatus.NEEDS_MANUAL && inPlan -> {
                reasons += "方案内课程「${name}」不允许处于需人工确认状态"
                return null
            }
        }
        return SourceCourse(
            courseCode = code,
            courseName = name,
            creditsText = creditsText,
            status = status,
            inPlan = inPlan,
            teacherNames = optionalText(item, "JSXM").orEmpty(),
            classCode = optionalText(item, "BJMC").orEmpty(),
            confirmationHint = optionalText(item, "note"),
        )
    }

    private fun parseCompletedOutsidePlan(
        item: JSONObject,
        displayIndex: Int,
        reasons: MutableList<String>,
    ): CompletedCourseOutsidePlan? {
        val label = "方案外已结课第 ${displayIndex} 项"
        val code = requiredText(item, "KCH", "$label 的课程代码", reasons) ?: return null
        val name = requiredText(item, "KCMC", "方案外已结课「${code}」的名称", reasons) ?: return null
        val term = requiredText(item, "XNXQDM", "方案外已结课「${code}」的学期", reasons) ?: return null
        if (!item.has("XF") || item.isNull("XF")) {
            reasons += "方案外已结课「${name}」缺少学分"
            return null
        }
        val creditsText = CreditsDecimal.normalizePositive(item.get("XF").toString())
        if (creditsText == null) {
            reasons += "方案外已结课「${name}」的学分不是合法正十进制数"
            return null
        }
        if (!item.has("CJ") || item.isNull("CJ")) {
            reasons += "方案外已结课「${name}」缺少成绩"
            return null
        }
        val scoreText = item.get("CJ").toString().trim()
        if (scoreText.isEmpty()) {
            reasons += "方案外已结课「${name}」的成绩为空白"
            return null
        }
        return CompletedCourseOutsidePlan(code, name, creditsText, term, scoreText)
    }

    private fun requiredObject(root: JSONObject, key: String, reasons: MutableList<String>): JSONObject? {
        val value = root.optJSONObject(key)
        if (value == null) {
            reasons += "缺少 ${key} 部分"
            return null
        }
        return value
    }

    private fun requiredText(
        obj: JSONObject,
        key: String,
        label: String,
        reasons: MutableList<String>,
    ): String? {
        if (!obj.has(key) || obj.isNull(key)) {
            reasons += "缺少${label}"
            return null
        }
        val text = obj.get(key).toString().trim()
        if (text.isEmpty()) {
            reasons += "${label}为空白"
            return null
        }
        return text
    }

    private fun optionalText(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.get(key).toString().trim().ifEmpty { null }
    }

    private fun requiredDecimal(
        obj: JSONObject,
        key: String,
        label: String,
        positive: Boolean,
        reasons: MutableList<String>,
    ): String? {
        if (!obj.has(key) || obj.isNull(key)) {
            reasons += "缺少${label}"
            return null
        }
        val raw = obj.get(key).toString()
        val normalized = if (positive) CreditsDecimal.normalizePositive(raw) else CreditsDecimal.normalizeNonNegative(raw)
        if (normalized == null) {
            reasons += "${label}不是合法${if (positive) "正" else "非负"}十进制数：${raw}"
            return null
        }
        return normalized
    }
}
