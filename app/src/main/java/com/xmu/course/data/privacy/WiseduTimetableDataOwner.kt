package com.xmu.course.data.privacy

import androidx.room.withTransaction
import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Wisedu 课表数据的隐私所有者。
 *
 * 数据范围 = 用户手动导入的课表本体：semesters + courses + timetables +
 * timetable_configs + skipped_courses。不含派生 Widget 数据（无独立 DataOwner）。
 *
 * 删除安全模型（Phase 5.3.2-C3）：
 * - 仅 Room DELETE，无网络、无同步、无认证清理；
 * - 显式顺序 skipped_courses → timetable_configs → timetables → courses → semesters，
 *   包在单个事务内（FK CASCADE 仅作兜底，不依赖隐式行为）；
 * - 单次只清本 Owner 范围，Todo（含 LOCAL/TRONCLASS）不受影响。
 *
 * [onTimetableSelectionCleared] 用于删除后重置组合根侧的
 * TimetablePrefs.currentTimetableId 悬空引用（Data Layer 偏好，非 Provider 数据本体）。
 */
class WiseduTimetableDataOwner(
    private val database: AppDatabase,
    private val onTimetableSelectionCleared: () -> Unit = {},
) : PrivacyDataOwner {

    override suspend fun countLocalData(): Int = coroutineScope {
        val semesters = async { database.semesterDao().countAll() }
        val courses = async { database.courseDao().countAll() }
        val timetables = async { database.timetableDao().countAll() }
        val configs = async { database.timetableDao().countConfigs() }
        val skipped = async { database.skippedCourseDao().countAll() }
        semesters.await() + courses.await() + timetables.await() + configs.await() + skipped.await()
    }

    override suspend fun clearLocalData() {
        database.withTransaction {
            database.skippedCourseDao().deleteAll()
            database.timetableDao().deleteAllConfigs()
            database.timetableDao().deleteAll()
            database.courseDao().deleteAll()
            database.semesterDao().deleteAll()
        }
        onTimetableSelectionCleared()
    }
}
