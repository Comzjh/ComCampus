package com.xmu.course.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xmu.course.domain.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startSection")
    fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startSection")
    suspend fun getBySemester(semesterId: Long): List<CourseEntity>

    /** 供待办课程选择器读取全部本地课程；不改变现有课表查询。 */
    @Query("SELECT * FROM courses ORDER BY name, id")
    suspend fun getAll(): List<CourseEntity>

    @Query("DELETE FROM courses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM courses WHERE semesterId = :semesterId")
    suspend fun countBySemester(semesterId: Long): Int

    @Insert
    suspend fun insertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Query("UPDATE courses SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: String)

    @Query("UPDATE courses SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("UPDATE courses SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE courses SET teacher = :teacher WHERE id = :id")
    suspend fun updateTeacher(id: Long, teacher: String)

    @Query("UPDATE courses SET location = :location WHERE id = :id")
    suspend fun updateLocation(id: Long, location: String)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 按课表查询课程（经 timetables.semesterId 关联，供课程管理页/Widget 使用）。 */
    @Query(
        "SELECT c.* FROM courses c JOIN timetables t ON t.semesterId = c.semesterId " +
            "WHERE t.id = :timetableId ORDER BY c.dayOfWeek, c.startSection",
    )
    fun observeByTimetableId(timetableId: Long): Flow<List<CourseEntity>>

    /** 按课表 + 星期查询课程（星期一=1 … 星期日=7，供今日课程/提醒使用）。 */
    @Query(
        "SELECT c.* FROM courses c JOIN timetables t ON t.semesterId = c.semesterId " +
            "WHERE t.id = :timetableId AND c.dayOfWeek = :dayOfWeek ORDER BY c.startSection",
    )
    fun observeByTimetableAndDay(timetableId: Long, dayOfWeek: Int): Flow<List<CourseEntity>>
}

