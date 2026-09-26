package com.xmu.course.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学期表。code 为金智学期代码（如 20261），唯一索引用于导入冲突判定。
 */
@Entity(
    tableName = "semesters",
    indices = [Index(value = ["code"], unique = true)],
)
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val name: String,
    val startDate: String? = null,
    val endDate: String? = null,
)
