package com.xmu.course.adapter.jw

import java.net.URLDecoder
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** cjcx querySetting 方言：官方最小条件集、%2Aorder 编码、分页参数与隐私约束。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CjcxRequestBuilderTest {

    private val body = CjcxRequestBuilder.gradesBody()

    @Test
    fun topLevelParametersMatchOfficialDialect() {
        val segments = body.split('&')
        assertTrue(segments[0].startsWith("querySetting="))
        assertTrue("排序键的 * 必须编码为 %2A", segments.contains("%2Aorder=-XNXQDM,-KCH,-KXH"))
        assertFalse("顶层不得出现未编码的 *order 键", segments.any { it.startsWith("*order=") })
        assertTrue(segments.contains("pageSize=${CjcxRequestBuilder.PAGE_SIZE}"))
        assertTrue(segments.contains("pageNumber=1"))
    }

    @Test
    fun querySettingCarriesExactMinimalConditionSet() {
        val encoded = body.substringAfter("querySetting=").substringBeforeLast("&%2Aorder=")
        val json = URLDecoder.decode(encoded, "UTF-8")
        val conditions = JSONArray(json)
        assertEquals(3, conditions.length())

        val sfyx = conditions.getJSONObject(0)
        assertEquals("SFYX", sfyx.getString("name"))
        assertEquals("1", sfyx.getString("value"))
        assertEquals("cbl_m_List", sfyx.getString("builderList"))
        assertEquals("m_value_equal", sfyx.getString("builder"))
        assertEquals("是", sfyx.getString("value_display"))

        val showMax = conditions.getJSONObject(1)
        assertEquals("SHOWMAXCJ", showMax.getString("name"))
        assertEquals(0, showMax.getInt("value"))
        assertEquals("cbl_String", showMax.getString("builderList"))
        assertEquals("equal", showMax.getString("builder"))
        assertEquals("否", showMax.getString("value_display"))

        val order = conditions.getJSONObject(2)
        assertEquals("*order", order.getString("name"))
        assertEquals(CjcxRequestBuilder.ORDER_VALUE, order.getString("value"))
        assertEquals("m_value_equal", order.getString("builder"))
    }

    @Test
    fun bodyNeverCarriesStudentIdentity() {
        // cjcx 按会话识别学生：请求体不得出现学号参数或占位符。
        assertFalse(body.contains("XH="))
        assertFalse(body.contains("{{XH}}"))
        assertFalse(body.contains("XNXQDM="))
    }
}
