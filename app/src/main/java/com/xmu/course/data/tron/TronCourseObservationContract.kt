package com.xmu.course.data.tron

import com.xmu.course.data.tronclass.model.TronCourseEntity
import kotlinx.coroutines.flow.Flow

/** 已同步畅课课程的本地只读观察能力。 */
interface TronCourseObservationContract {
    fun observeCourses(): Flow<List<TronCourseEntity>>
}
