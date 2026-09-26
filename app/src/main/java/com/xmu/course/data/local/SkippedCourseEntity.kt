package com.xmu.course.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户本地“翘课”标记。
 *
 * 只记录课程 ID，不记录日期、周次或次数；删除课程时状态级联清理。
 */
@Entity(
    tableName = "skipped_courses",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["courseId"], unique = true)],
)
data class SkippedCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val courseId: Long,
    val createdAt: Long,
)
