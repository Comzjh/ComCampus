package com.xmu.course.contracts.timetable

import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.flow.Flow

/**
 * Timetable feature 的 capability facade。
 *
 * 实现由组合根提供；调用方不需要知道 Room、偏好存储或学校系统的实现细节。
 */
interface TimetableFeatureRepository {
    fun observeCurrentTimetableState(): Flow<TimetableFeatureState>

    suspend fun addCourse(course: Course)

    suspend fun updateCourseColor(courseId: Long, color: String)

    suspend fun updateCourseNote(courseId: Long, note: String)

    suspend fun setCourseSkipped(courseId: Long, skipped: Boolean)

    suspend fun updateConfig(config: TimetableConfig)
}
