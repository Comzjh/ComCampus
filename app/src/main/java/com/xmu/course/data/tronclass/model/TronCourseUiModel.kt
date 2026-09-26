package com.xmu.course.data.tronclass.model

/**
 * TronClass 课程的 feature-facing 展示模型。
 *
 * 与 Room 缓存实体分离，避免 UI 依赖持久化字段或注解模型。
 */
data class TronCourseUiModel(
    val id: Long,
    val name: String,
    val instructor: String,
    val semester: String,
)

fun TronCourseEntity.toUiModel(): TronCourseUiModel = TronCourseUiModel(
    id = tronCourseId,
    name = name,
    instructor = instructor,
    semester = semester,
)
