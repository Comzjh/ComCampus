package com.xmu.course.data.tronclass.assignment

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** homework-activities 的网络 DTO；字段保持可空，结构校验集中在 Parser。 */
@JsonClass(generateAdapter = false)
data class AssignmentDto(
    val id: Long?,
    val title: String?,
    val description: String?,
    @Json(name = "course_id") val courseId: Long?,
    @Json(name = "due_at") val dueAt: String?,
    @Json(name = "end_time") val endTime: String?,
)

@JsonClass(generateAdapter = false)
data class AssignmentPageDto(
    @Json(name = "homework_activities") val homeworkActivities: List<AssignmentDto>?,
    val pages: Int?,
    val page: Int?,
    @Json(name = "page_size") val pageSize: Int?,
)

data class TronAssignmentPage(
    val assignments: List<TronAssignment>,
    val pages: Int?,
)

data class TronAssignment(
    val externalId: String,
    val title: String,
    val description: String,
    val remoteCourseId: Long?,
    val deadline: Long?,
)
