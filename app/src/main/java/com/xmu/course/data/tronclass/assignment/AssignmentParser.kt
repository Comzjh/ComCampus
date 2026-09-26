package com.xmu.course.data.tronclass.assignment

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** 将作业响应严格转换为同步领域模型，不为缺失必填字段制造默认作业。 */
class AssignmentParser(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(AssignmentPageDto::class.java)

    fun parseJson(json: String): TronResult<TronAssignmentPage> {
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

    fun parse(response: AssignmentPageDto): TronResult<TronAssignmentPage> {
        val activities = response.homeworkActivities
            ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingAssignments))
        val assignments = ArrayList<TronAssignment>(activities.size)
        activities.forEach { dto ->
            val id = dto.id
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingAssignmentId))
            if (id <= 0L) {
                return TronResult.Error(TronClassError.ParseError(ParseErrorReason.InvalidAssignmentId))
            }
            val title = dto.title?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingAssignmentTitle))
            val rawDeadline = dto.dueAt?.trim()?.takeIf { it.isNotEmpty() }
                ?: dto.endTime?.trim()?.takeIf { it.isNotEmpty() }
            val deadline = rawDeadline?.let { parseDeadline(it) }
                ?: if (rawDeadline == null) null else {
                    return TronResult.Error(
                        TronClassError.ParseError(ParseErrorReason.InvalidAssignmentDeadline),
                    )
                }
            assignments += TronAssignment(
                externalId = id.toString(),
                title = title,
                description = dto.description.orEmpty().trim(),
                remoteCourseId = dto.courseId,
                deadline = deadline,
            )
        }
        return TronResult.Success(TronAssignmentPage(assignments, response.pages))
    }

    private fun parseDeadline(raw: String): Long? = runCatching {
        raw.toLongOrNull()?.let { numeric ->
            return@runCatching if (numeric < 10_000_000_000L) numeric * 1000L else numeric
        }
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).atZone(zoneId).toInstant().toEpochMilli() }.getOrNull()
            ?: LocalDate.parse(raw).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }.getOrNull()
}
