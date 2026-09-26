package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TronCourseParserTest {
    private val parser = TronCourseParser { 99_000L }

    @Test
    fun `正常JSON转换为课程实体`() {
        val result = parser.parseJson(
            """
            {"courses":[{"id":10384,"name":"Python程序设计","semester":{"code":"2026F","name":"2026-2027秋季","real_name":"2026-2027秋季"},"instructors":[{"name":"测试教师"}]}]}
            """.trimIndent(),
        )

        assertTrue(result is TronResult.Success)
        val course = (result as TronResult.Success).value.single()
        assertEquals(10384L, course.tronCourseId)
        assertEquals("Python程序设计", course.name)
        assertEquals("2026-2027秋季", course.semester)
        assertEquals("测试教师", course.instructor)
        assertEquals(99_000L, course.updatedTime)
    }

    @Test
    fun `缺少必要课程字段返回解析错误`() {
        val result = parser.parseJson("""{"courses":[{"name":"缺少课程ID"}]}""")

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingCourseId),
            result.errorOrNull(),
        )
    }

    @Test
    fun `教师和学期为空时允许并归一化为空字符串`() {
        val result = parser.parseJson("""{"courses":[{"id":7,"name":"无教师课程"}]}""")

        assertTrue(result is TronResult.Success)
        val course = (result as TronResult.Success).value.single()
        assertEquals("", course.semester)
        assertEquals("", course.instructor)
    }

    @Test
    fun `响应结构变化不会生成错误课程`() {
        val result = parser.parseJson("""{"courses":{}}""")

        assertEquals(TronClassError.ResponseFormatChanged, result.errorOrNull())
    }

    private fun <T> TronResult<T>.errorOrNull(): TronClassError? =
        (this as? TronResult.Error)?.error
}
