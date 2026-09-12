package com.xmu.course.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xmu.course.domain.Semester
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {

    @Query("SELECT * FROM semesters ORDER BY code DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters ORDER BY code DESC")
    suspend fun getAll(): List<SemesterEntity>

    @Query("SELECT * FROM semesters WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): SemesterEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(semester: SemesterEntity): Long

    @Query("SELECT * FROM semesters WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SemesterEntity?

    @Query("DELETE FROM semesters WHERE id = :semesterId")
    suspend fun deleteById(semesterId: Long)

    @Query("UPDATE semesters SET startDate = :startDate WHERE id = :id")
    suspend fun updateStartDate(id: Long, startDate: String)
}

/** 供 Repository 使用的扩展转换。 */
fun SemesterEntity.toDomain() = Semester(
    id = id, code = code, name = name, startDate = startDate, endDate = endDate,
)
