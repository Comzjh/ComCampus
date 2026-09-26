package com.xmu.course.data.academiccompletion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 来源快照解析器的固定测试。
 *
 * 全部为合成数据：课程名、学分、日期均为虚构值，
 * 不复用任何真实学生数据（隐私规则：真实快照不得进入测试 fixture）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAcademicSnapshotParserTest {

    private fun snapshotJson(
        courses: String = """
            [{"KCDM":"C001","KCMC":"合成课程甲","XF":3,"in_plan":true,"xf_status":"confirmed","JSXM":"合成教师","BJMC":"01"},
             {"KCDM":"C002","KCMC":"合成课程乙","XF":0.25,"in_plan":true,"xf_status":"confirmed"},
             {"KCDM":"C003","KCMC":"合成课程丙","XF":null,"in_plan":false,"xf_status":"需人工确认","note":"合成提示"}]
        """.trimIndent(),
        planLevelTotal: String = "3.25",
        totalsSum: String = "3.25",
        snapshotAt: String = "2026-09-16 22:09",
        completed: String = """
            [{"KCH":"X001","KCMC":"合成课外的课","XF":2,"XNXQDM":"2025-2026-2","CJ":88}]
        """.trimIndent(),
        includeTotals: Boolean = true,
        extraRootFields: String = "",
    ): String {
        val totalsFragment = if (includeTotals) {
            "\"totals\": {\"in_plan_xf_sum\": " + totalsSum + "}" + extraRootFields
        } else {
            "\"other\": 1"
        }
        return """
        {
          "generated_at": "2026-09-19",
          "student": {"XH": "00000000000000", "XM": "合成姓名"},
          "semester": {"jwapp_code": "2026-2027-1"},
          "plan": {
            "name": "合成方案",
            "required_xf": 160,
            "earned_xf_snapshot": 40,
            "snapshot_czsj": "$snapshotAt",
            "this_semester_selected_xf_plan_level": $planLevelTotal
          },
          "courses": $courses,
          "faw_completed_courses": $completed,
          ${totalsFragment}
        }
    """.trimIndent()
    }


    private fun parseOk(raw: String): AcademicCompletionSnapshot {
        val result = LocalAcademicSnapshotParser.parse(raw)
        assertTrue("expected success, got ${(result as? SnapshotImportResult.Rejected)?.reasons}", result is SnapshotImportResult.Success)
        return (result as SnapshotImportResult.Success).snapshot
    }

    private fun rejectReasons(raw: String): List<String> {
        val result = LocalAcademicSnapshotParser.parse(raw)
        assertTrue("expected rejection", result is SnapshotImportResult.Rejected)
        return (result as SnapshotImportResult.Rejected).reasons
    }

    @Test
    fun validSnapshotAcceptedWithNormalizedDecimals() {
        val snapshot = parseOk(snapshotJson())
        assertEquals(1, snapshot.schemaVersion)
        assertEquals("合成方案", snapshot.plan.planName)
        assertEquals("3.25", snapshot.plan.sourceThisSemesterTotalText)
        assertEquals("3", snapshot.enrolledCourses[0].creditsText)
        assertEquals("0.25", snapshot.enrolledCourses[1].creditsText)
        assertEquals(SourceXfStatus.NEEDS_MANUAL, snapshot.enrolledCourses[2].status)
        assertEquals("合成提示", snapshot.enrolledCourses[2].confirmationHint)
        assertEquals(1, snapshot.completedCoursesOutsidePlan.size)
        assertEquals("88", snapshot.completedCoursesOutsidePlan[0].scoreText)
        assertTrue(snapshot.localOverrides.isEmpty())
    }

    @Test
    fun studentIdentityNeverEntersInternalModel() {
        val snapshot = parseOk(snapshotJson())
        val encoded = AcademicCompletionSnapshotCodec.encode(snapshot)
        assertTrue("内部快照不得包含姓名", !encoded.contains("合成姓名"))
        assertTrue("内部快照不得包含学号", !encoded.contains("00000000000000"))
    }

    @Test
    fun planReconciliationMismatchRejected() {
        val reasons = rejectReasons(snapshotJson(planLevelTotal = "4.00"))
        assertTrue(reasons.any { it.contains("不一致") })
    }

    @Test
    fun totalsFieldMismatchRejected() {
        val reasons = rejectReasons(snapshotJson(totalsSum = "9.99"))
        assertTrue(reasons.any { it.contains("自相矛盾") })
    }

    @Test
    fun missingRequiredFieldRejected() {
        val reasons = rejectReasons(snapshotJson(includeTotals = false))
        assertTrue(reasons.any { it.contains("totals") })
    }

    @Test
    fun malformedCreditRejected() {
        val reasons = rejectReasons(
            snapshotJson(
                courses = """[{"KCDM":"C001","KCMC":"坏学分课","XF":"abc","in_plan":true,"xf_status":"confirmed"}]""",
                planLevelTotal = "0",
                totalsSum = "0",
            ),
        )
        assertTrue(reasons.any { it.contains("合法正十进制") })
    }

    @Test
    fun negativeCreditRejected() {
        val reasons = rejectReasons(
            snapshotJson(courses = """[{"KCDM":"C001","KCMC":"负学分课","XF":-3,"in_plan":true,"xf_status":"confirmed"}]"""),
        )
        assertTrue(reasons.isNotEmpty())
    }

    @Test
    fun duplicateCourseIdentityRejected() {
        val reasons = rejectReasons(
            snapshotJson(courses = """
                [{"KCDM":"C001","KCMC":"重复课A","XF":3,"in_plan":true,"xf_status":"confirmed"},
                 {"KCDM":"C001","KCMC":"重复课B","XF":0.25,"in_plan":true,"xf_status":"confirmed"}]
            """.trimIndent()),
        )
        assertTrue(reasons.any { it.contains("重复") })
    }

    @Test
    fun unknownXfStatusRejected() {
        val reasons = rejectReasons(
            snapshotJson(courses = """[{"KCDM":"C001","KCMC":"怪状态课","XF":3,"in_plan":true,"xf_status":"maybe"}]"""),
        )
        assertTrue(reasons.any { it.contains("无法识别") })
    }

    @Test
    fun confirmedCourseWithoutCreditsRejected() {
        val reasons = rejectReasons(
            snapshotJson(courses = """[{"KCDM":"C001","KCMC":"缺学分课","in_plan":true,"xf_status":"confirmed"}]"""),
        )
        assertTrue(reasons.any { it.contains("缺少学分") })
    }

    @Test
    fun needsManualCourseWithCreditsRejected() {
        val reasons = rejectReasons(
            snapshotJson(courses = """[{"KCDM":"C001","KCMC":"矛盾课","XF":2,"in_plan":false,"xf_status":"需人工确认"}]"""),
        )
        assertTrue(reasons.any { it.contains("矛盾") })
    }

    @Test
    fun inPlanNeedsManualRejected() {
        val reasons = rejectReasons(
            snapshotJson(courses = """[{"KCDM":"C001","KCMC":"方案内待确认课","XF":null,"in_plan":true,"xf_status":"需人工确认"}]"""),
        )
        assertTrue(reasons.any { it.contains("不允许") })
    }

    @Test
    fun invalidSnapshotTimestampRejected() {
        val reasons = rejectReasons(snapshotJson(snapshotAt = "昨天"))
        assertTrue(reasons.any { it.contains("快照时间格式") })
    }

    @Test
    fun unknownExtraFieldsTolerated() {
        val snapshot = parseOk(
            snapshotJson(extraRootFields = ""","future_field":{"whatever":[1,2,3]}"""),
        )
        assertEquals(3, snapshot.enrolledCourses.size)
    }

    @Test
    fun nonJsonFileRejected() {
        val reasons = rejectReasons("not a json at all")
        assertTrue(reasons.any { it.contains("JSON") })
    }
}
