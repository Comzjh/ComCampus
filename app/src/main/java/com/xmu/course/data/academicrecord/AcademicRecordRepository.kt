package com.xmu.course.data.academicrecord

import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.data.local.AcademicRecordDao
import com.xmu.course.data.local.toContractOrNull
import com.xmu.course.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** AcademicRecord 的本地存储边界；Room entity 不泄漏到上层。 */
class AcademicRecordRepository(
    private val dao: AcademicRecordDao,
) {
    suspend fun save(record: AcademicRecord): Long = dao.insert(record.toEntity())

    suspend fun getAll(): List<AcademicRecord> = dao.getAll().mapNotNull { it.toContractOrNull() }

    fun observeAll(): Flow<List<AcademicRecord>> = dao.observeAll().map { records ->
        records.mapNotNull { it.toContractOrNull() }
    }

    suspend fun clear() = dao.deleteAll()
}
