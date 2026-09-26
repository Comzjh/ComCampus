package com.xmu.course.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource

/** AcademicRecord 的本地存储模型，不向 UI 或 Feature 暴露。 */
@Entity(tableName = "academic_records")
data class AcademicRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val creditsText: String,
    val source: String,
)

fun AcademicRecord.toEntity(): AcademicRecordEntity = AcademicRecordEntity(
    name = name,
    creditsText = creditsText,
    source = source.name,
)

/** 未知来源不进入 Feature 读取结果，避免把无法解释的旧值伪装成合法来源。 */
fun AcademicRecordEntity.toContractOrNull(): AcademicRecord? = runCatching {
    AcademicRecord(
        name = name,
        creditsText = creditsText,
        source = AcademicRecordSource.valueOf(source),
    )
}.getOrNull()
