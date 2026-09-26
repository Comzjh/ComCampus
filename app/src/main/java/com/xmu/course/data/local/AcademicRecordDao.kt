package com.xmu.course.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademicRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AcademicRecordEntity): Long

    @Query("SELECT * FROM academic_records ORDER BY id ASC")
    suspend fun getAll(): List<AcademicRecordEntity>

    @Query("SELECT * FROM academic_records ORDER BY id ASC")
    fun observeAll(): Flow<List<AcademicRecordEntity>>

    @Query("SELECT COUNT(*) FROM academic_records")
    suspend fun countAll(): Int

    @Query("DELETE FROM academic_records")
    suspend fun deleteAll()
}
