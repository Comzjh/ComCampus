package com.xmu.course.data.tronclass.todo

import com.xmu.course.data.tronclass.api.TronSemesterDto
import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialTodoParserTest {
    private val parser = OfficialTodoParser()

    @Test
    fun `只把exam解析为官方畅课待办候选并忽略其他类型`() {
        val result = parser.parseJson(
            """
            {"todo_list":[
              {"id":101,"title":"日常练习","course_id":9001,"course_name":"Python","course_code":"P01","end_time":"2026-09-20T23:59:00+08:00","type":"exam","is_student":true,"is_locked":false},
              {"id":102,"title":"作业","course_id":9001,"end_time":"2026-09-21T00:00:00Z","type":"homework"},
              {"id":103,"title":"课堂测验","course_id":9001,"type":"classroom_exam"},
              {"id":104,"title":"未知","course_id":9001,"type":"quiz"}
            ]}
            """.trimIndent(),
        )

        val candidate = (result as TronResult.Success).value.single()
        assertEquals(101L, candidate.remoteId)
        assertEquals("日常练习", candidate.title)
        assertEquals(9001L, candidate.courseId)
        assertEquals("Python", candidate.courseName)
        assertEquals(
            Instant.parse("2026-09-20T15:59:00Z").toEpochMilli(),
            candidate.deadline,
        )
    }

    @Test
    fun `缺少exam id返回解析错误`() {
        val result = parser.parseJson(
            """{"todo_list":[{"title":"无ID","course_id":9001,"type":"exam"}]}""",
        )

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoId),
            (result as TronResult.Error).error,
        )
    }

    @Test
    fun `非法exam id不能生成候选`() {
        val result = parser.parseJson(
            """{"todo_list":[{"id":0,"title":"非法ID","course_id":9001,"type":"exam"}]}""",
        )

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.InvalidOfficialTodoId),
            (result as TronResult.Error).error,
        )
    }

    @Test
    fun `缺少exam课程id返回解析错误`() {
        val result = parser.parseJson(
            """{"todo_list":[{"id":101,"title":"无课程","type":"exam"}]}""",
        )

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoCourseId),
            (result as TronResult.Error).error,
        )
    }

    @Test
    fun `缺少标题返回解析错误`() {
        val result = parser.parseJson(
            """{"todo_list":[{"id":101,"title":"  ","course_id":9001,"type":"exam"}]}""",
        )

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoTitle),
            (result as TronResult.Error).error,
        )
    }

    @Test
    fun `UTC和带offset的截止时间按绝对时间解析`() {
        val result = parser.parseJson(
            """{"todo_list":[
                {"id":101,"title":"UTC","course_id":9001,"end_time":"2026-09-20T15:59:00Z","type":"exam"},
                {"id":102,"title":"Offset","course_id":9001,"end_time":"2026-09-20T23:59:00+08:00","type":"exam"}
            ]}""",
        )

        val candidates = (result as TronResult.Success).value
        assertEquals(candidates[0].deadline, candidates[1].deadline)
    }

    @Test
    fun `空截止时间保留为null`() {
        val result = parser.parseJson(
            """{"todo_list":[{"id":101,"title":"无截止","course_id":9001,"end_time":null,"type":"exam"}]}""",
        )

        assertEquals(null, (result as TronResult.Success).value.single().deadline)
    }

    @Test
    fun `非法截止时间安全失败`() {
        val result = parser.parseJson(
            """{"todo_list":[{"id":101,"title":"非法时间","course_id":9001,"end_time":"not-a-date","type":"exam"}]}""",
        )

        assertTrue(result is TronResult.Error)
        assertEquals(
            TronClassError.ParseError(ParseErrorReason.InvalidOfficialTodoDeadline),
            (result as TronResult.Error).error,
        )
    }

    @Test
    fun `空列表是成功而非错误`() {
        assertEquals(
            TronResult.Success(emptyList<OfficialTodoCandidate>()),
            parser.parseJson("{\"todo_list\":[]}"),
        )
    }

    @Test
    fun `缺少列表返回格式错误`() {
        val result = parser.parseJson("{\"items\":[]}")

        assertEquals(
            TronClassError.ParseError(ParseErrorReason.MissingOfficialTodos),
            (result as TronResult.Error).error,
        )
    }
}
