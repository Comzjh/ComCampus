package com.xmu.course.data.privacy

import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.data.local.AppDatabase

/**
 * AcademicImport 学业记录的隐私所有者。
 *
 * 数据范围 = academic_records。归属决策沿用 5.3.2-B 审计：
 * 该表来自用户手动导入的官方文件（PDF/Excel），与 JW Provider 解耦，
 * 因此挂 AcademicImport 而不是 xmu.jw。
 *
 * 删除安全模型（Phase 5.3.2-C3）：
 * - 复用既有 AcademicRecordDao.deleteAll()，零新增 SQL；
 * - Grades/GPA Sandbox 均从该表读取，无独立持久化副本，删除后自动更新为空；
 * - 仅 Room DELETE，无网络、无同步、无认证清理。
 */
class AcademicImportDataOwner(private val database: AppDatabase) : PrivacyDataOwner {

    override suspend fun countLocalData(): Int = database.academicRecordDao().countAll()

    override suspend fun clearLocalData() {
        database.academicRecordDao().deleteAll()
    }
}
