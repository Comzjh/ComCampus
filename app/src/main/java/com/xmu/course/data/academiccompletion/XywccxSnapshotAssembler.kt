package com.xmu.course.data.academiccompletion

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/** 组装结果：成功携带来源格式 JSON（可直接进 LocalAcademicSnapshotParser）；失败只含安全原因。 */
sealed interface AssembleResult {
    data class Success(val sourceJson: String) : AssembleResult

    /** 原因只描述缺失/非法的字段路径，绝不携带任何字段值或学生数据。 */
    data class Invalid(val reason: String) : AssembleResult
}

/**
 * xywccx EMAP 响应 → 来源快照格式（与 LocalAcademicSnapshotParser 的输入契约一致）。
 *
 * 设计原则：
 * - 复用既有 fail-closed 解析与对账管线，本类只负责结构映射，不猜测任何值；
 * - 学分缺失（方案外+在修）→ `xf_status="需人工确认"` 且 XF=null，绝不回填；
 * - join 规则：gsapp 课表 KCDM == xywccx 课程池 KCH；未命中即方案外；
 * - 输出 JSON 不含姓名/学号（身份从不进入 Kotlin 层）。
 */
object XywccxSnapshotAssembler {

    /** 组装所需的全部端点响应（EMAP 信封或 gsapp 原生结构）。 */
    data class Inputs(
        val currentSemester: JSONObject,
        val plans: JSONObject,
        val completionSnapshot: JSONObject,
        val planSemesterTotal: JSONObject,
        val coursePool: JSONObject,
        val outsidePlanCompleted: JSONObject,
        val semesterCourses: JSONObject,
        val generatedAtDate: String,
    )

    fun assemble(inputs: Inputs): AssembleResult {
        val semesterCode = firstString(
            inputs.currentSemester, "cxdqxnxq", "DM",
        ) ?: return AssembleResult.Invalid("缺少当前学期代码（cxdqxnxq.rows[0].DM）")
        val gsappCode = mapGsappSemesterCode(semesterCode)
            ?: return AssembleResult.Invalid("学期代码无法映射到 gsapp 方言：$semesterCode")

        val planRow = selectMainPlan(inputs.plans)
            ?: return AssembleResult.Invalid("缺少个人培养方案（grpyfacx.rows 为空）")
        val planCode = planRow.optString("PYFADM").ifBlank { null }
            ?: return AssembleResult.Invalid("培养方案缺少 PYFADM")
        val planName = planRow.optString("PYFAMC").ifBlank { null }
            ?: return AssembleResult.Invalid("培养方案缺少 PYFAMC")
        val requiredXf = normalized(planRow.opt("ZSYQXF"), positive = true)
            ?: return AssembleResult.Invalid("培养方案缺少合法总学分（ZSYQXF）")

        val snapshotRow = selectSnapshotRow(inputs.completionSnapshot, planCode)
            ?: return AssembleResult.Invalid("缺少完成度快照（cxxsscfa.rows 为空）")
        val earnedXf = normalized(snapshotRow.opt("WCXF"), positive = false)
            ?: return AssembleResult.Invalid("完成度快照缺少合法已获学分（WCXF）")
        val snapshotAt = snapshotRow.optString("CZSJ").ifBlank { null }
            ?: return AssembleResult.Invalid("完成度快照缺少计算时间（CZSJ）")

        val planLevelXkxf = firstDecimal(
            inputs.planSemesterTotal, "cxfakzyxxfgj", "XKXF", positive = false,
        ) ?: return AssembleResult.Invalid("缺少方案级本学期已选学分（cxfakzyxxfgj.rows[0].XKXF）")

        // 课程池：存在性（in_plan 依据）与学分字典分开维护。
        // 池内命中但学分缺失/非法 → 保留 in_plan=true 且 XF=null，
        // 由下游 fail-closed 解析器整单拒绝，绝不静默降级为"方案外"。
        val poolCodes = LinkedHashSet<String>()
        val creditDict = LinkedHashMap<String, String>()
        val poolRows = rowsOf(inputs.coursePool, "cxscfakzkc_xsyx")
            ?: return AssembleResult.Invalid("缺少课程池（queryKzkcXsyx datas.rows）")
        for (index in 0 until poolRows.length()) {
            val row = poolRows.optJSONObject(index) ?: continue
            val code = row.optString("KCH")
            if (code.isBlank()) continue
            if (code in poolCodes) continue
            poolCodes += code
            normalized(row.opt("XF"), positive = true)?.let { creditDict[code] = it }
        }

        // 本学期课表课程（gsapp）：结构兼容 pkjgList / data.pkjgList / EMAP rows。
        val courseRows = semesterCourseRows(inputs.semesterCourses)
            ?: return AssembleResult.Invalid("本学期课表响应结构未识别（期望 pkjgList 或 rows）")
        val courses = JSONArray()
        val seenCourseCodes = mutableSetOf<String>()
        var inPlanSum = BigDecimal.ZERO
        var inPlanCount = 0
        for (index in 0 until courseRows.length()) {
            val row = courseRows.optJSONObject(index) ?: continue
            val code = row.optString("KCDM")
            if (code.isBlank()) continue
            if (!seenCourseCodes.add(code)) continue
            val name = row.optString("KCMC")
            if (name.isBlank()) continue
            val inPlan = code in poolCodes
            val poolCredit = creditDict[code]
            val course = JSONObject()
                .put("KCDM", code)
                .put("KCMC", name)
                .put("in_plan", inPlan)
                .put("JSXM", row.optString("JSXM"))
                .put("BJMC", row.optString("BJMC"))
            if (poolCredit != null) {
                course.put("XF", poolCredit)
                course.put("xf_status", "confirmed")
                inPlanSum += BigDecimal(poolCredit)
                inPlanCount += 1
            } else {
                // 方案外+在修：系统限制导致结课前无学分可查；置 null 并要求人工确认。
                // （池内命中但学分非法的情况同样置 null，由解析器整单拒绝。）
                course.put("XF", JSONObject.NULL)
                course.put("xf_status", "需人工确认")
                course.put(
                    "note",
                    if (inPlan) "官方课程池该课学分缺失，数据异常，请重新刷新"
                    else "方案外课程，结课前官方接口无学分数据，请手动确认",
                )
            }
            courses.put(course)
        }

        // 方案外已结课（KZH=FAWKC）。
        val completed = JSONArray()
        val fawRows = rowsOf(inputs.outsidePlanCompleted, "cxscfakzkc_xsyx").orEmptyArray()
        for (index in 0 until fawRows.length()) {
            val row = fawRows.optJSONObject(index) ?: continue
            val code = row.optString("KCH")
            if (code.isBlank()) continue
            val name = row.optString("KCM")
            if (name.isBlank()) continue
            val xf = normalized(row.opt("XF"), positive = true) ?: continue
            val term = row.optString("XNXQDM")
            if (term.isBlank()) continue
            val score = row.opt("CJ")?.toString()?.trim().orEmpty()
            if (score.isEmpty()) continue
            completed.put(
                JSONObject()
                    .put("KCH", code)
                    .put("KCMC", name)
                    .put("XF", xf)
                    .put("XNXQDM", term)
                    .put("CJ", score),
            )
        }

        val inPlanSumText = creditsOf(inPlanSum)
        val root = JSONObject()
            .put("generated_at", inputs.generatedAtDate)
            .put("source", "xywccx_live_refresh")
            .put(
                "semester",
                JSONObject().put("jwapp_code", semesterCode).put("gsapp_code", gsappCode),
            )
            .put(
                "plan",
                JSONObject()
                    .put("PYFADM", planCode)
                    .put("name", planName)
                    .put("required_xf", requiredXf)
                    .put("earned_xf_snapshot", earnedXf)
                    .put("snapshot_czsj", normalizeTimestamp(snapshotAt))
                    .put("this_semester_selected_xf_plan_level", planLevelXkxf),
            )
            .put("courses", courses)
            .put("faw_completed_courses", completed)
            .put(
                "totals",
                JSONObject()
                    .put("in_plan_courses", inPlanCount)
                    .put("in_plan_xf_sum", inPlanSumText)
                    .put(
                        "reconciled_with_plan_level_xkxf",
                        BigDecimal(inPlanSumText).compareTo(BigDecimal(planLevelXkxf)) == 0,
                    ),
            )
        return AssembleResult.Success(root.toString())
    }

    /**
     * jwapp 学期码 → gsapp 学期码：`2026-2027-1` → `20261`（起始年 + 学期序号）。
     * 已是 gsapp 形态（5 位数字）时原样通过；无法映射返回 null。
     */
    fun mapGsappSemesterCode(jwappCode: String): String? {
        val trimmed = jwappCode.trim()
        Regex("^(\\d{4})-\\d{4}-(\\d{1,2})$").find(trimmed)?.let { match ->
            val (startYear, term) = match.destructured
            return "$startYear$term"
        }
        if (Regex("^\\d{5}$").matches(trimmed)) return trimmed
        return null
    }

    /** 主修方案选择：XDLXDM=="01" 优先，否则首行；协调器与组装器共用同一规则。 */
    internal fun selectMainPlan(plans: JSONObject): JSONObject? {
        val rows = rowsOf(plans, "grpyfacx") ?: return null
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("XDLXDM") == "01") return row
        }
        return rows.optJSONObject(0)
    }

    private fun selectSnapshotRow(snapshot: JSONObject, planCode: String): JSONObject? {
        val rows = rowsOf(snapshot, "cxxsscfa") ?: return null
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("PYFADM") == planCode) return row
        }
        return rows.optJSONObject(0)
    }

    private fun semesterCourseRows(response: JSONObject): JSONArray? {
        response.optJSONArray("pkjgList")?.let { return it }
        response.optJSONObject("data")?.optJSONArray("pkjgList")?.let { return it }
        // 兼容 EMAP 信封形态。
        val datas = response.optJSONObject("datas") ?: return null
        datas.keys().forEach { key ->
            datas.optJSONObject(key)?.optJSONArray("rows")?.let { return it }
        }
        return null
    }

    private fun rowsOf(envelope: JSONObject, action: String): JSONArray? =
        envelope.optJSONObject("datas")?.optJSONObject(action)?.optJSONArray("rows")

    private fun firstString(envelope: JSONObject, action: String, field: String): String? {
        val row = rowsOf(envelope, action)?.optJSONObject(0) ?: return null
        return row.optString(field).ifBlank { null }
    }

    private fun firstDecimal(
        envelope: JSONObject,
        action: String,
        field: String,
        positive: Boolean,
    ): String? {
        val row = rowsOf(envelope, action)?.optJSONObject(0) ?: return null
        return normalized(row.opt(field), positive)
    }

    private fun normalized(value: Any?, positive: Boolean): String? {
        if (value == null || value == JSONObject.NULL) return null
        return if (positive) {
            CreditsDecimal.normalizePositive(value.toString())
        } else {
            CreditsDecimal.normalizeNonNegative(value.toString())
        }
    }

    private fun creditsOf(sum: BigDecimal): String =
        CreditsDecimal.normalizeNonNegative(sum.toPlainString()) ?: "0"

    /** EMAP 时间戳可能是 "2026-09-16 22:09:00" 或带 T；parser 接受两种，这里仅去首尾空白。 */
    private fun normalizeTimestamp(raw: String): String = raw.trim()

    private fun JSONArray?.orEmptyArray(): JSONArray = this ?: JSONArray()
}
