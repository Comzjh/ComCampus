package com.xmu.course.data.academiccompletion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * xywccx EMAP → 来源快照组装测试。
 *
 * 全部使用合成数据（测试学生/示例课程），不复制集成包真实样本。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XywccxSnapshotAssemblerTest {

    private fun envelope(action: String, rows: JSONArray): JSONObject =
        JSONObject().put(
            "code", "0",
        ).put("datas", JSONObject().put(action, JSONObject().put("rows", rows).put("totalSize", rows.length())))

    private fun rows(vararg objects: JSONObject): JSONArray = JSONArray().apply { objects.forEach { put(it) } }

    private fun semesterEnvelope(code: String = "2026-2027-1") =
        envelope("cxdqxnxq", rows(JSONObject().put("DM", code)))

    private fun plansEnvelope(creditlessPlan: Boolean = false) = envelope(
        "grpyfacx",
        rows(
            JSONObject().put("PYFADM", "PLANB").put("PYFAMC", "辅修方案").put("XDLXDM", "02").put("ZSYQXF", "60"),
            JSONObject().put("PYFADM", "PLAN01").put("PYFAMC", "化学类主修方案").put("XDLXDM", "01").put(
                "ZSYQXF", if (creditlessPlan) "" else "148.0",
            ),
        ),
    )

    private fun snapshotEnvelope() = envelope(
        "cxxsscfa",
        rows(
            JSONObject()
                .put("PYFADM", "PLAN01")
                .put("WCXF", "55.0")
                .put("YQXF", "148.0")
                .put("CZSJ", "2026-09-16 22:09:00"),
        ),
    )

    private fun planTotalEnvelope(xkxf: String) =
        envelope("cxfakzyxxfgj", rows(JSONObject().put("XKXF", xkxf)))

    private fun poolEnvelope(vararg courses: Triple<String, String, String?>): JSONObject {
        val array = JSONArray()
        courses.forEach { (code, name, xf) ->
            val row = JSONObject().put("KCH", code).put("KCM", name)
            if (xf != null) row.put("XF", xf)
            array.put(row)
        }
        return envelope("cxscfakzkc_xsyx", array)
    }

    private fun semesterCoursesEnvelope(vararg courses: Pair<String, String>): JSONObject {
        val list = JSONArray()
        courses.forEach { (code, name) ->
            list.put(
                JSONObject()
                    .put("KCDM", code).put("KCMC", name)
                    .put("JSXM", "教师甲").put("BJMC", "示例班1"),
            )
        }
        return JSONObject().put("pkjgList", list)
    }

    private fun inputs(
        semester: JSONObject = semesterEnvelope(),
        plans: JSONObject = plansEnvelope(),
        snapshot: JSONObject = snapshotEnvelope(),
        planTotal: JSONObject = planTotalEnvelope("8.5"),
        pool: JSONObject = poolEnvelope(
            Triple("C001", "示例课程A", "3"),
            Triple("C002", "示例课程B", "2"),
            Triple("C003", "示例课程C", "3.5"),
        ),
        faw: JSONObject = envelope(
            "cxscfakzkc_xsyx",
            rows(
                JSONObject()
                    .put("KCH", "X900").put("KCM", "无机示例（I）")
                    .put("XF", "2").put("XNXQDM", "2025-2026-2").put("CJ", "66"),
            ),
        ),
        courses: JSONObject = semesterCoursesEnvelope("C001" to "示例课程A", "C002" to "示例课程B", "C003" to "示例课程C"),
    ) = XywccxSnapshotAssembler.Inputs(
        currentSemester = semester,
        plans = plans,
        completionSnapshot = snapshot,
        planSemesterTotal = planTotal,
        coursePool = pool,
        outsidePlanCompleted = faw,
        semesterCourses = courses,
        generatedAtDate = "2026-09-20",
    )

    @Test
    fun happyPathProducesSnapshotAcceptedByExistingParser() {
        val result = XywccxSnapshotAssembler.assemble(inputs())
        assertTrue(result is AssembleResult.Success)
        val parsed = LocalAcademicSnapshotParser.parse((result as AssembleResult.Success).sourceJson)
        assertTrue("parser must accept assembled snapshot: ${(parsed as? SnapshotImportResult.Rejected)?.reasons}", parsed is SnapshotImportResult.Success)
        val snapshot = (parsed as SnapshotImportResult.Success).snapshot
        assertEquals(3, snapshot.enrolledCourses.size)
        assertEquals("8.5", snapshot.plan.sourceThisSemesterTotalText)
        assertEquals("55", snapshot.plan.earnedCreditsText)
        assertEquals("148", snapshot.plan.requiredCreditsText)
        assertEquals("化学类主修方案", snapshot.plan.planName)
        assertEquals(1, snapshot.completedCoursesOutsidePlan.size)
        assertEquals("无机示例（I）", snapshot.completedCoursesOutsidePlan[0].courseName)
        assertTrue(snapshot.pendingManualCourses.isEmpty())
    }

    @Test
    fun outsidePlanInProgressCourseRequiresManualConfirmation() {
        val result = XywccxSnapshotAssembler.assemble(
            inputs(
                planTotal = planTotalEnvelope("5"),
                courses = semesterCoursesEnvelope("C001" to "示例课程A", "C002" to "示例课程B", "OUT1" to "方案外在修课"),
            ),
        )
        assertTrue(result is AssembleResult.Success)
        val parsed = LocalAcademicSnapshotParser.parse((result as AssembleResult.Success).sourceJson)
        assertTrue(parsed is SnapshotImportResult.Success)
        val snapshot = (parsed as SnapshotImportResult.Success).snapshot
        val manual = snapshot.pendingManualCourses
        assertEquals(1, manual.size)
        assertEquals("OUT1", manual[0].courseCode)
        assertEquals(null, manual[0].creditsText)
        assertEquals(SourceXfStatus.NEEDS_MANUAL, manual[0].status)
        assertEquals(false, manual[0].inPlan)
    }

    @Test
    fun reconciliationMismatchIsRejectedNotSilentlyFixed() {
        // 方案内求和 8.5 != 方案级 99 → 解析器整单拒绝（数据过期信号）。
        val result = XywccxSnapshotAssembler.assemble(inputs(planTotal = planTotalEnvelope("99")))
        assertTrue(result is AssembleResult.Success)
        val parsed = LocalAcademicSnapshotParser.parse((result as AssembleResult.Success).sourceJson)
        assertTrue(parsed is SnapshotImportResult.Rejected)
    }

    @Test
    fun poolCourseWithInvalidCreditFailsClosedInsteadOfOutsidePlanDowngrade() {
        val result = XywccxSnapshotAssembler.assemble(
            inputs(
                pool = poolEnvelope(
                    Triple("C001", "示例课程A", "3"),
                    Triple("C002", "示例课程B", "2"),
                    Triple("C003", "示例课程C", null),
                ),
            ),
        )
        assertTrue(result is AssembleResult.Success)
        val json = JSONObject((result as AssembleResult.Success).sourceJson)
        val courseC = json.getJSONArray("courses").let { array ->
            (0 until array.length()).map(array::getJSONObject).first { it.getString("KCDM") == "C003" }
        }
        assertTrue("must stay in_plan", courseC.getBoolean("in_plan"))
        assertTrue(courseC.isNull("XF"))
        // 方案内+需人工确认 属矛盾数据，解析器必须整单拒绝。
        assertTrue(LocalAcademicSnapshotParser.parse(json.toString()) is SnapshotImportResult.Rejected)
    }

    @Test
    fun duplicateSemesterCourseCodesAreDeduplicated() {
        val result = XywccxSnapshotAssembler.assemble(
            inputs(
                courses = semesterCoursesEnvelope("C001" to "示例课程A", "C001" to "示例课程A"),
            ),
        )
        assertTrue(result is AssembleResult.Success)
        val json = JSONObject((result as AssembleResult.Success).sourceJson)
        assertEquals("duplicate KCDM rows must collapse to one course", 1, json.getJSONArray("courses").length())
    }

    @Test
    fun missingPlanIsInvalid() {
        val result = XywccxSnapshotAssembler.assemble(
            inputs(plans = envelope("grpyfacx", JSONArray())),
        )
        assertTrue(result is AssembleResult.Invalid)
    }

    @Test
    fun unknownSemesterCourseShapeIsInvalid() {
        val result = XywccxSnapshotAssembler.assemble(
            inputs(courses = JSONObject().put("unexpected", true)),
        )
        assertTrue(result is AssembleResult.Invalid)
    }

    @Test
    fun gsappSemesterCodeMapping() {
        assertEquals("20261", XywccxSnapshotAssembler.mapGsappSemesterCode("2026-2027-1"))
        assertEquals("20262", XywccxSnapshotAssembler.mapGsappSemesterCode("2026-2027-2"))
        assertEquals("20263", XywccxSnapshotAssembler.mapGsappSemesterCode("2026-2027-3"))
        assertEquals("20261", XywccxSnapshotAssembler.mapGsappSemesterCode("20261"))
        assertEquals(null, XywccxSnapshotAssembler.mapGsappSemesterCode("bad-code"))
    }

    @Test
    fun emptySemesterCoursesProduceConsistentZeroTotals() {
        val result = XywccxSnapshotAssembler.assemble(
            inputs(
                planTotal = planTotalEnvelope("0"),
                courses = semesterCoursesEnvelope(),
            ),
        )
        assertTrue(result is AssembleResult.Success)
        val parsed = LocalAcademicSnapshotParser.parse((result as AssembleResult.Success).sourceJson)
        assertTrue(parsed is SnapshotImportResult.Success)
    }
}
