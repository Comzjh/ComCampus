package com.xmu.course.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SkippedCourseDao {

    @Query("SELECT courseId FROM skipped_courses")
    fun observeSkippedCourseIds(): Flow<List<Long>>

    @Query("SELECT courseId FROM skipped_courses")
    suspend fun getSkippedCourseIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SkippedCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SkippedCourseEntity)

    @Query("DELETE FROM skipped_courses WHERE courseId IN (:courseIds)")
    suspend fun deleteByCourseIds(courseIds: List<Long>)

    @Query("DELETE FROM skipped_courses WHERE courseId = :courseId")
    suspend fun deleteByCourseId(courseId: Long)
}
