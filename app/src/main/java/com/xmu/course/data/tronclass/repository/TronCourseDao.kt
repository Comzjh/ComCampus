package com.xmu.course.data.tronclass.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.xmu.course.data.tronclass.model.TronCourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TronCourseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<TronCourseEntity>)

    /** 合并同步结果，保留本地已经缓存的历史学期。 */
    @Transaction
    suspend fun upsertAllPreservingHistory(courses: List<TronCourseEntity>) {
        insertAll(courses)
    }

    @Query("SELECT * FROM tron_courses ORDER BY semester, name, tronCourseId")
    suspend fun getAll(): List<TronCourseEntity>

    @Query("SELECT * FROM tron_courses ORDER BY semester, name, tronCourseId")
    fun observeAll(): Flow<List<TronCourseEntity>>

    @Query("SELECT COUNT(*) FROM tron_courses")
    suspend fun countAll(): Int

    @Query("DELETE FROM tron_courses")
    suspend fun deleteAll()

    /** 在同一个 Room 事务内完成全量替换；调用者传入的列表必须已经完成解析和校验。 */
    @Transaction
    suspend fun replaceAll(courses: List<TronCourseEntity>) {
        deleteAll()
        insertAll(courses)
    }
}
