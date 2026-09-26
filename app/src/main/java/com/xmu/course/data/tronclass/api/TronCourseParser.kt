package com.xmu.course.data.tronclass.api

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.tronclass.model.TronResult
import java.io.IOException

/** 将已解析或原始 JSON 课程响应严格转换为 Room Entity。 */
class TronCourseParser(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(TronCoursesResponseDto::class.java)

    fun parseJson(json: String): TronResult<List<TronCourseEntity>> {
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

    fun parse(response: TronCoursesResponseDto): TronResult<List<TronCourseEntity>> {
        val courses = response.courses
            ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingCourses))
        val updatedTime = clock()
        val entities = ArrayList<TronCourseEntity>(courses.size)
        courses.forEach { dto ->
            val courseId = dto.id
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingCourseId))
            if (courseId <= 0L) {
                return TronResult.Error(TronClassError.ParseError(ParseErrorReason.InvalidCourseId))
            }
            val name = dto.name?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingCourseName))
            val semester = listOf(
                dto.semester?.realName,
                dto.semester?.name,
                dto.semester?.code,
            ).firstOrNull { !it.isNullOrBlank() }.orEmpty().trim()
            val instructor = dto.instructors.orEmpty()
                .asSequence()
                .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
                .toList()
                .joinToString("、")
                .ifBlank { dto.instructor.orEmpty().trim() }
            entities += TronCourseEntity(
                tronCourseId = courseId,
                name = name,
                semester = semester,
                instructor = instructor,
                updatedTime = updatedTime,
            )
        }
        return TronResult.Success(entities)
    }

    fun activeCourseIds(response: TronCoursesResponseDto, activeSemesterId: Long): Set<Long> =
        response.courses.orEmpty()
            .asSequence()
            .filter { it.semester?.id == activeSemesterId }
            .mapNotNull { it.id }
            .toSet()

    fun knownInactiveCourseIds(response: TronCoursesResponseDto, activeSemesterId: Long): Set<Long> =
        response.courses.orEmpty()
            .asSequence()
            .filter { it.semester?.id != null && it.semester.id != activeSemesterId }
            .mapNotNull { it.id }
            .toSet()
}
