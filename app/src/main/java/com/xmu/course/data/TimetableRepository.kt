package com.xmu.course.data

import android.util.Log
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.SemesterEntity
import com.xmu.course.data.local.TimetableConfigEntity
import com.xmu.course.data.local.TimetableWithCount as EntityWithCount
import com.xmu.course.data.local.toDomain
import com.xmu.course.data.local.toEntity
import com.xmu.course.contracts.TimetableImportContract
import com.xmu.course.contracts.TimetableManagementContract
import com.xmu.course.contracts.TimetableObservationContract
import com.xmu.course.contracts.TimetableSummary
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 多课表仓库。
 *
 * 自定义课表通过隐藏学期（code = "custom-*"）承载课程，
 * 导入课表则复用导入时创建的学期；两者在删除时的处理不同。
 */
class TimetableRepository(private val db: AppDatabase) :
    TimetableImportContract,
    TimetableManagementContract,
    TimetableObservationContract {

    private val dao get() = db.timetableDao()

    /** 观察全部课表（含课程数量，按创建时间倒序）。 */
    override fun observeTimetables(): Flow<List<TimetableSummary>> =
        dao.observeAllWithCount().map { list -> list.map { it.toDomain() } }

    suspend fun getTimetable(id: Long): Timetable? = dao.getById(id)?.toDomain()

    /** 持续观察单个课表（当前课表切换用）。 */
    override fun observeTimetable(id: Long): Flow<Timetable?> =
        dao.observeById(id).map { it?.toDomain() }

    /**
     * 创建课表：自动创建隐藏学期 + 默认外观配置。
     * @return 新课表（含生成的 id）
     */
    override suspend fun createTimetable(name: String): Timetable =
        createTimetable(name, startDate = null)

    suspend fun createTimetable(name: String, startDate: String?): Timetable {
        val now = System.currentTimeMillis()
        val semesterId = db.semesterDao().insert(
            SemesterEntity(code = "custom-$now", name = name),
        )
        val timetableId = dao.insert(
            com.xmu.course.data.local.TimetableEntity(
                name = name,
                semesterId = semesterId,
                startDate = startDate,
                createdTime = now,
            ),
        )
        dao.upsertConfig(TimetableConfigEntity(timetableId = timetableId))
        val created = Timetable(
            id = timetableId,
            name = name,
            semesterId = semesterId,
            startDate = startDate,
            createdTime = now,
        )
        Log.d(TAG, "createTimetable: id=$timetableId, semesterId=$semesterId, name=$name")
        return created
    }

    /**
     * 导入成功后调用：为该学期绑定课表；已绑定则直接返回（幂等）。
     * 与 [createTimetable] 不同，不创建隐藏学期，直接复用导入学期。
     */
    override suspend fun ensureForSemester(semesterId: Long, name: String, startDate: String?): Timetable {
        getBySemesterId(semesterId)?.let {
            Log.d(TAG, "ensureForSemester: semesterId=$semesterId already bound to timetable ${it.id}")
            return it
        }
        val now = System.currentTimeMillis()
        val timetableId = dao.insert(
            com.xmu.course.data.local.TimetableEntity(
                name = name,
                semesterId = semesterId,
                startDate = startDate,
                createdTime = now,
            ),
        )
        dao.upsertConfig(TimetableConfigEntity(timetableId = timetableId))
        Log.d(TAG, "ensureForSemester: bound timetable id=$timetableId to semesterId=$semesterId")
        return Timetable(
            id = timetableId,
            name = name,
            semesterId = semesterId,
            startDate = startDate,
            createdTime = now,
        )
    }

    suspend fun getBySemesterId(semesterId: Long): Timetable? =
        dao.getBySemesterId(semesterId)?.toDomain()

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    /** 修改"当前周"（手动设置真实当前周，与查看周选择器区分）。 */
    suspend fun updateCurrentWeek(id: Long, week: Int) = dao.updateCurrentWeek(id, week)

    suspend fun setStartDate(id: Long, date: String) = dao.updateStartDate(id, date)

    suspend fun setColor(id: Long, color: String) = dao.updateColor(id, color)

    /**
     * 删除课表。
     * 自定义课表（隐藏学期承载）连同学期与课程一起删除；
     * 导入课表只删除课表记录，学期与课程保留。
     */
    override suspend fun deleteTimetable(id: Long) {
        val timetable = dao.getById(id) ?: return
        val semester = db.semesterDao().getById(timetable.semesterId)
        if (semester != null && semester.code.startsWith("custom-")) {
            db.semesterDao().deleteById(semester.id) // FK CASCADE 级联课程与课表
        } else {
            dao.deleteById(id)
        }
        Log.d(TAG, "deleteTimetable: id=$id, customSemester=${semester?.code?.startsWith("custom-") == true}")
    }

    /** 观察某课表的外观配置（无记录时给默认值）。 */
    fun observeConfig(timetableId: Long): Flow<TimetableConfig> =
        dao.observeConfig(timetableId).map { it?.toDomain() ?: TimetableConfig(timetableId = timetableId) }

    suspend fun getConfig(timetableId: Long): TimetableConfig =
        dao.getConfig(timetableId)?.toDomain() ?: TimetableConfig(timetableId = timetableId)

    suspend fun saveConfig(config: TimetableConfig) = dao.upsertConfig(config.toEntity())

    /** 课表设置页统一入口：整份保存。 */
    suspend fun updateConfig(config: TimetableConfig) = saveConfig(config)

    /** 更新单张课表背景（其他字段保持不变）。 */
    suspend fun updateBackground(config: TimetableConfig) = saveConfig(config)

    private fun EntityWithCount.toDomain() =
        TimetableSummary(
            timetable = timetable.toDomain(),
            courseCount = courseCount,
            isCustom = isCustom,
        )

    companion object {
        private const val TAG = "XmuTimetable"
    }
}
