package com.xmu.course.contracts

import com.xmu.course.domain.Course
import kotlinx.coroutines.flow.Flow

/** CourseManagerViewModel 所需的最小课程管理能力。 */
interface CourseManagementContract {
    fun observeCoursesByTimetable(timetableId: Long): Flow<List<Course>>

    fun observeSkippedCourseIds(): Flow<Set<Long>>

    suspend fun addCourse(semesterId: Long, course: Course)

    suspend fun updateCourseName(courseId: Long, name: String)

    suspend fun updateCourseTeacher(courseId: Long, teacher: String)

    suspend fun updateCourseLocation(courseId: Long, location: String)

    suspend fun updateCourseNote(courseId: Long, note: String)

    suspend fun updateCourseColor(courseId: Long, color: String)

    suspend fun saveSkippedCourses(courseIds: Collection<Long>)

    suspend fun deleteSemesterCourses(semesterId: Long)

    suspend fun deleteCourse(courseId: Long)
}
