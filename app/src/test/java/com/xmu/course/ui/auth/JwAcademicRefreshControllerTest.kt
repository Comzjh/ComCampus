package com.xmu.course.ui.auth

import com.xmu.course.data.academiccompletion.AcademicRefreshOutcome
import com.xmu.course.data.jwgrades.GradeRefreshOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 手动刷新结果文案映射与 JS 返回解码测试。
 *
 * 只锁安全展示纪律：同源失败去重、未登录/角色提示分离、
 * 结果串绝不回显响应体/身份/凭据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwAcademicRefreshControllerTest {

    @Test
    fun bothSuccessProducesTwoDistinctSegments() {
        val message = JwAcademicRefreshController.describe(
            academic = AcademicRefreshOutcome.Success(courseCount = 12, pendingManualCount = 2),
            grades = GradeRefreshOutcome.Success(entryCount = 30, semesterCount = 6),
        )
        assertTrue(message.contains("培养方案已更新"))
        assertTrue(message.contains("在修 12 门"))
        assertTrue(message.contains("成绩单已更新"))
        assertTrue(message.contains("30 条记录"))
    }

    @Test
    fun sharedAuthFailureIsDedupedToSingleSegment() {
        val message = JwAcademicRefreshController.describe(
            academic = AcademicRefreshOutcome.AuthRequired,
            grades = GradeRefreshOutcome.AuthRequired,
        )
        assertTrue(message.contains("未登录教务"))
        assertEquals(1, message.split("；").size)
    }

    @Test
    fun academicSuccessWithGradeRoleGateShowsBothHints() {
        val message = JwAcademicRefreshController.describe(
            academic = AcademicRefreshOutcome.Success(courseCount = 5, pendingManualCount = 0),
            grades = GradeRefreshOutcome.RoleContextRequired,
        )
        assertTrue(message.contains("培养方案已更新"))
        assertTrue(message.contains("确认学生身份"))
    }

    @Test
    fun serverFailureMessageSignalsCachePreserved() {
        val message = JwAcademicRefreshController.describe(
            academic = AcademicRefreshOutcome.ServerUnavailable,
            grades = GradeRefreshOutcome.ServerUnavailable,
        )
        assertTrue(message.contains("本机数据保持不变"))
    }

    @Test
    fun decodeJsResultUnwrapsQuotedStringAndPassesJson() {
        assertEquals("hello", decodeJsResult("\"hello\""))
        assertEquals("""{"origin":"x"}""", decodeJsResult("""{"origin":"x"}"""))
        assertNull(decodeJsResult("null"))
        assertNull(decodeJsResult(null))
        assertNull(decodeJsResult(""))
    }

    @Test
    fun decodeJsResultPreservesDoubleEncodedJsonString() {
        // 双重编码：JS 侧已 JSON.stringify，evaluateJavascript 再包一层字符串字面量。
        val inner = """{"state":"done","status":200}"""
        val encoded = "\"" + inner.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        assertEquals(inner, decodeJsResult(encoded))
    }
}
