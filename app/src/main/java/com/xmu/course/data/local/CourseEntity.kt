package com.xmu.course.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource

/**
 * 课程表。weeks 以逗号分隔周号存储（如 "1,2,3,5,7"），Domain 层为 Set<Int>。
 */
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId"), Index("dayOfWeek")],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val semesterId: Long,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val duration: Int,
    val weeks: String,
    val source: String,
    val color: String,
    val note: String,
)

/** weeks 字符串 ↔ Set<Int> 互转。 */
fun parseWeeks(raw: String): Set<Int> =
    raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

fun weeksToString(weeks: Set<Int>): String = weeks.sorted().joinToString(",")

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startSection = startSection,
    duration = duration,
    weeks = parseWeeks(weeks),
    source = runCatching { CourseSource.valueOf(source) }.getOrDefault(CourseSource.MANUAL),
    color = color,
    note = note,
)

fun Course.toEntity(semesterId: Long): CourseEntity = CourseEntity(
    id = id,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startSection = startSection,
    duration = duration,
    weeks = weeksToString(weeks),
    source = source.name,
    color = color,
    note = note,
)
