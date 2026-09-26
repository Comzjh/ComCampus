package com.xmu.course.data.tronclass.todo

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `/api/todos` 的最小网络模型；只声明当前 exam 导入所需字段。 */
@JsonClass(generateAdapter = false)
data class OfficialTodoItemDto(
    val id: Long?,
    val title: String?,
    @Json(name = "course_id") val courseId: Long?,
    @Json(name = "course_name") val courseName: String?,
    @Json(name = "course_code") val courseCode: String?,
    @Json(name = "end_time") val endTime: String?,
    val type: String?,
    @Json(name = "is_student") val isStudent: Boolean?,
    @Json(name = "is_locked") val isLocked: Boolean?,
)

@JsonClass(generateAdapter = false)
data class OfficialTodoResponseDto(
    @Json(name = "todo_list") val todoList: List<OfficialTodoItemDto>?,
)
