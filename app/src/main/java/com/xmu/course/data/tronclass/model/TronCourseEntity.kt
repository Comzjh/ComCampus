package com.xmu.course.data.tronclass.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TronClass 课程缓存。
 *
 * 该表独立于现有课表课程表：id 是本地行主键，tronCourseId 是 TronClass 远端课程 ID。
 */
@Entity(
    tableName = "tron_courses",
    indices = [Index(value = ["tronCourseId"], unique = true)],
)
data class TronCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tronCourseId: Long,
    val name: String,
    val semester: String,
    val instructor: String,
    val updatedTime: Long,
)
