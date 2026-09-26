package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.assignment.AssignmentPageDto
import com.xmu.course.data.tronclass.todo.OfficialTodoResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TronClassApiService {
    @GET("api/my-semesters")
    suspend fun getMySemesters(): Response<TronSemestersResponseDto>

    @GET("api/my-courses")
    suspend fun getMyCourses(): Response<TronCoursesResponseDto>

    @GET("api/courses/{courseId}/homework-activities")
    suspend fun getHomeworkActivities(
        @Path("courseId") courseId: Long,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): Response<AssignmentPageDto>

    @GET("api/todos")
    suspend fun getOfficialTodos(): Response<OfficialTodoResponseDto>
}
