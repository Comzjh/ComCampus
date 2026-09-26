package com.xmu.course.contracts.todo.model

/**
 * Todo 关联课程的来源限定引用。
 *
 * 同一个 Long 在不同来源下不是同一门课程；具体来源到存储/Provider ID 的解释由 Adapter 负责。
 */
sealed interface TodoCourseReference {
    val id: Long

    data class Local(override val id: Long) : TodoCourseReference

    data class External(override val id: Long) : TodoCourseReference
}

/** 迁移期供旧写入 command/UI 文案使用的 Feature source 映射。 */
val TodoCourseReference.featureSource: TodoFeatureSource
    get() = when (this) {
        is TodoCourseReference.Local -> TodoFeatureSource.LOCAL
        is TodoCourseReference.External -> TodoFeatureSource.EXTERNAL
    }
