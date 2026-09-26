package com.xmu.course.data.tronclass.assignment

import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentParserTest {
    private val parser = AssignmentParser(ZoneOffset.UTC)

    @Test
    fun `正常响应解析为作业领域模型`() {
        val result = parser.parseJson(
            """
            {"homework_activities":[{"id":123,"title":"测试作业","description":"完成练习","course_id":9001,"due_at":"2026-09-20T23:59:00Z"}],"pages":1}
            """.trimIndent(),
        )

        val page = (result as TronResult.Success).value
        assertEquals("123", page.assignments.single().externalId)
        assertEquals("测试作业", page.assignments.single().title)
        assertEquals(9001L, page.assignments.single().remoteCourseId)
        assertEquals(Instant.parse("2026-09-20T23:59:00Z").toEpochMilli(), page.assignments.single().deadline)
    }

    @Test
    fun `缺少作业id返回解析错误`() {
        val result = parser.parseJson(
            """{"homework_activities":[{"title":"无ID作业"}]}""",
        )

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingAssignmentId),
            (result as TronResult.Error).error,
        )
    }

    @Test
    fun `兼容end_time并允许可选教师描述为空`() {
        val result = parser.parseJson(
            """{"homework_activities":[{"id":124,"title":"兼容时间","end_time":"2026-09-20T23:59"}]}""",
        )

        val assignment = (result as TronResult.Success).value.assignments.single()
        assertEquals(Instant.parse("2026-09-20T23:59:00Z").toEpochMilli(), assignment.deadline)
        assertEquals("", assignment.description)
    }

    @Test
    fun `没有截止时间时保留为空`() {
        val result = parser.parseJson(
            """{"homework_activities":[{"id":126,"title":"无截止时间"}]}""",
        )

        val assignment = (result as TronResult.Success).value.assignments.single()
        assertEquals(null, assignment.deadline)
    }

    @Test
    fun `格式变化和非法时间不会生成作业`() {
        val missingList = parser.parseJson("{\"items\":[]}")
        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingAssignments),
            (missingList as TronResult.Error).error,
        )

        val invalidTime = parser.parseJson(
            """{"homework_activities":[{"id":125,"title":"非法时间","due_at":"not-a-date"}]}""",
        )
        assertTrue(invalidTime is TronResult.Error)
        assertEquals(
            TronClassError.ParseError(ParseErrorReason.InvalidAssignmentDeadline),
            (invalidTime as TronResult.Error).error,
        )
    }
}
