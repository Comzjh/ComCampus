package com.xmu.course.data.jwgrades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

/**
 * cjcx 解析测试：全合成数据（示例课程A/B、学期 2025-2026-x），
 * 覆盖成功聚合、合格制 "N/A" 绩点、fail-closed 行校验、分页截断拒绝与身份字段排除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CjcxGradeParserTest {

    private fun payload(rowsJson: String, totalSize: Int): JSONObject =
        JSONObject(
            """{"code":"0","datas":{"xscjcx":{"totalSize":$totalSize,"rows":$rowsJson}}}""",
        )

    @Test
    fun parsesSyntheticRowsWithCreditSumAndPassFailPointText() {
        val rows = """[
            {"XNXQDM":"2025-2026-2","XNXQDM_DISPLAY":"2025-2026学年第二学期","KCH":"C001","KCM":"示例课程A",
             "XF":3,"ZCJ":87,"XFJD":"3.7","KCXZDM_DISPLAY":"必修","KCLBDM_DISPLAY":"公共课",
             "KKDWDM_DISPLAY":"示例单位","CXCKDM":"","WID":"W1","XH":"00000000000000"},
            {"XNXQDM":"2025-2026-2","XNXQDM_DISPLAY":"2025-2026学年第二学期","KCH":"C002","KCM":"示例课程B",
             "XF":2.5,"ZCJ":"合格","XFJD":"N/A","KCXZDM_DISPLAY":"选修","WID":"W2","XH":"00000000000000"},
            {"XNXQDM":"2025-2026-1","KCH":"C003","KCM":"示例课程C","XF":2,"ZCJ":60,"XFJD":"1.0"}
        ]"""
        val result = CjcxGradeParser.parseGrades(payload(rows, totalSize = 3))
        assertTrue(result is CjcxGradeParser.ParseResult.Success)
        val success = result as CjcxGradeParser.ParseResult.Success
        assertEquals(3, success.entries.size)
        assertEquals("7.5", success.totalCreditsText)

        val first = success.entries[0]
        assertEquals("W1", first.rowId)
        assertEquals("3", first.creditsText)
        assertEquals("87", first.gradeText)
        assertEquals("3.7", first.pointGradeText)
        assertEquals("示例单位", first.offeringUnitDisplay)
        assertEquals(null, first.retakeCode)

        val passFail = success.entries[1]
        assertEquals("合格", passFail.gradeText)
        assertEquals("N/A", passFail.pointGradeText)
        assertEquals("2.5", passFail.creditsText)

        // 无 WID / 无显示名 / 无学期 DISPLAY 的行：确定性合成行号 + 学期代码回退。
        val minimal = success.entries[2]
        assertEquals("2025-2026-1#C003#2", minimal.rowId)
        assertEquals("2025-2026-1", minimal.semesterDisplay)
        assertEquals(null, minimal.courseNatureDisplay)

        // 学期分组保持首现顺序。
        val snapshot = JwGradeSnapshot(
            refreshedAtEpochMillis = 1L,
            providerId = "xmu.jw",
            sourceCapability = "cjcx.xscjcx",
            totalCreditsText = success.totalCreditsText,
            entries = success.entries,
        )
        assertEquals(
            listOf("2025-2026-2", "2025-2026-1"),
            snapshot.groupedBySemester().map { it.first },
        )
    }

    @Test
    fun missingRequiredFieldRejectsWholeSnapshot() {
        val rows = """[{"XNXQDM":"2025-2026-1","KCH":"C001","XF":3,"ZCJ":87}]"""
        val result = CjcxGradeParser.parseGrades(payload(rows, totalSize = 1))
        assertTrue(result is CjcxGradeParser.ParseResult.Rejected)
    }

    @Test
    fun nonNumericCreditsRejectsWholeSnapshot() {
        val rows = """[{"XNXQDM":"2025-2026-1","KCH":"C001","KCM":"示例课程A","XF":"未知","ZCJ":87}]"""
        val result = CjcxGradeParser.parseGrades(payload(rows, totalSize = 1))
        assertTrue(result is CjcxGradeParser.ParseResult.Rejected)
    }

    @Test
    fun truncatedPageIsRejectedToAvoidSilentPartialData() {
        val rows = """[{"XNXQDM":"2025-2026-1","KCH":"C001","KCM":"示例课程A","XF":3,"ZCJ":87}]"""
        val result = CjcxGradeParser.parseGrades(payload(rows, totalSize = 99))
        assertTrue(result is CjcxGradeParser.ParseResult.Rejected)
        assertEquals("分页不完整：totalSize=99 rows=1", (result as CjcxGradeParser.ParseResult.Rejected).reason)
    }

    @Test
    fun emptyResultIsSuccessWithNoEntries() {
        val result = CjcxGradeParser.parseGrades(payload("[]", totalSize = 0))
        val success = result as CjcxGradeParser.ParseResult.Success
        assertTrue(success.entries.isEmpty())
        assertEquals("0", success.totalCreditsText)
    }

    @Test
    fun missingEnvelopeStructureIsRejected() {
        assertTrue(
            CjcxGradeParser.parseGrades(JSONObject("""{"code":"0"}"""))
                is CjcxGradeParser.ParseResult.Rejected,
        )
    }
}
