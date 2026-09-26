package com.xmu.course.data.tronclass.api

import com.squareup.moshi.JsonClass

/** TronClass 当前课程接口 DTO；字段保持可空，缺失字段由 Parser 显式处理。 */
@JsonClass(generateAdapter = false)
data class TronCourseDto(
    val id: Long?,
    val name: String?,
    val semester: TronSemesterDto?,
    val instructors: List<TronInstructorDto>?,
    /** 兼容社区 SDK 文档中的旧扁平字段；当前厦大响应以 instructors 为准。 */
    val instructor: String?,
)

@JsonClass(generateAdapter = false)
data class TronSemesterDto(
    val code: String?,
    val name: String?,
    @com.squareup.moshi.Json(name = "real_name") val realName: String?,
    val id: Long? = null,
    val sort: Int? = null,
    @com.squareup.moshi.Json(name = "academic_year_id") val academicYearId: Long? = null,
    @com.squareup.moshi.Json(name = "is_active") val isActive: Boolean? = null,
)

@JsonClass(generateAdapter = false)
data class TronInstructorDto(
    val name: String?,
)

@JsonClass(generateAdapter = false)
data class TronCoursesResponseDto(
    val courses: List<TronCourseDto>?,
)

@JsonClass(generateAdapter = false)
data class TronSemestersResponseDto(
    val semesters: List<TronSemesterDto>?,
)
