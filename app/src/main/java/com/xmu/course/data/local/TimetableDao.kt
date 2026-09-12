package com.xmu.course.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 课表列表项（含课程数量、是否自定义课表）。 */
data class TimetableWithCount(
    @Embedded val timetable: TimetableEntity,
    val courseCount: Int,
    /** 学期 code 以 custom- 开头 → 自定义课表（删除时需级联学期与课程）。 */
    val isCustom: Boolean,
)

@Dao
interface TimetableDao {

    @Query(
        "SELECT t.*, (SELECT COUNT(*) FROM courses c WHERE c.semesterId = t.semesterId) AS courseCount, " +
            "(CASE WHEN s.code LIKE 'custom-%' THEN 1 ELSE 0 END) AS isCustom " +
            "FROM timetables t JOIN semesters s ON s.id = t.semesterId ORDER BY t.createdTime DESC",
    )
    fun observeAllWithCount(): Flow<List<TimetableWithCount>>

    @Query("SELECT * FROM timetables ORDER BY createdTime DESC")
    suspend fun getAll(): List<TimetableEntity>

    @Query("SELECT * FROM timetables WHERE id = :id")
    suspend fun getById(id: Long): TimetableEntity?

    @Query("SELECT * FROM timetables WHERE id = :id")
    fun observeById(id: Long): Flow<TimetableEntity?>

    @Query("SELECT * FROM timetables WHERE semesterId = :semesterId LIMIT 1")
    suspend fun getBySemesterId(semesterId: Long): TimetableEntity?

    @Insert
    suspend fun insert(timetable: TimetableEntity): Long

    @Query("UPDATE timetables SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE timetables SET currentWeek = :week WHERE id = :id")
    suspend fun updateCurrentWeek(id: Long, week: Int)

    @Query("UPDATE timetables SET startDate = :date WHERE id = :id")
    suspend fun updateStartDate(id: Long, date: String)

    @Query("UPDATE timetables SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: String)

    @Query("DELETE FROM timetables WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- 外观配置 ----

    @Query("SELECT * FROM timetable_configs WHERE timetableId = :id")
    fun observeConfig(id: Long): Flow<TimetableConfigEntity?>

    @Query("SELECT * FROM timetable_configs WHERE timetableId = :id")
    suspend fun getConfig(id: Long): TimetableConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: TimetableConfigEntity)
}
