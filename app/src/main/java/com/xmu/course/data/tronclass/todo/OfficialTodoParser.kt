package com.xmu.course.data.tronclass.todo

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

data class OfficialTodoCandidate(
    val remoteId: Long,
    val title: String,
    val courseId: Long,
    val courseName: String?,
    val deadline: Long?,
)

/** 将官方聚合待办响应严格转换为可导入的 exam 候选。 */
class OfficialTodoParser {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(OfficialTodoResponseDto::class.java)

    fun parseJson(json: String): TronResult<List<OfficialTodoCandidate>> {
        if (json.isBlank()) return TronResult.Error(TronClassError.ResponseFormatChanged)
        return try {
            val response = adapter.fromJson(json)
                ?: return TronResult.Error(TronClassError.ResponseFormatChanged)
            parse(response)
        } catch (_: JsonDataException) {
            TronResult.Error(TronClassError.ResponseFormatChanged)
        } catch (_: IOException) {
            TronResult.Error(TronClassError.ResponseFormatChanged)
        }
    }

    fun parse(response: OfficialTodoResponseDto): TronResult<List<OfficialTodoCandidate>> {
        val items = response.todoList
            ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingOfficialTodos))
        val candidates = ArrayList<OfficialTodoCandidate>()
        items.forEach { item ->
            if (item.type != EXAM_TYPE) return@forEach

            val id = item.id
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoId))
            if (id <= 0L) {
                return TronResult.Error(TronClassError.ParseError(ParseErrorReason.InvalidOfficialTodoId))
            }
            val title = item.title?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoTitle))
            val courseId = item.courseId
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoCourseId))
            if (courseId <= 0L) {
                return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingOfficialTodoCourseId))
            }
            val rawDeadline = item.endTime?.trim()?.takeIf { it.isNotEmpty() }
            val deadline = rawDeadline?.let(::parseDeadline)
                ?: if (rawDeadline == null) null else {
                    return TronResult.Error(
                        TronClassError.ParseError(ParseErrorReason.InvalidOfficialTodoDeadline),
                    )
                }
            candidates += OfficialTodoCandidate(
                remoteId = id,
                title = title,
                courseId = courseId,
                courseName = item.courseName?.trim()?.takeIf { it.isNotEmpty() },
                deadline = deadline,
            )
        }
        return TronResult.Success(candidates)
    }

    private fun parseDeadline(raw: String): Long? =
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()

    companion object {
        const val EXAM_TYPE = "exam"
    }
}
