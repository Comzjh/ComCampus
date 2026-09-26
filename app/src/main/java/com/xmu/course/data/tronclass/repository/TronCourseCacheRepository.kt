package com.xmu.course.data.tronclass.repository

import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.tron.TronCourseObservationContract
import kotlinx.coroutines.flow.Flow

/** 只读暴露已同步的畅课课程，供运行时关联使用；不参与网络同步。 */
class TronCourseCacheRepository(private val dao: TronCourseDao) : TronCourseObservationContract {
    override fun observeCourses(): Flow<List<TronCourseEntity>> = dao.observeAll()
}
