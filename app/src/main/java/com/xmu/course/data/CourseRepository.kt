package com.xmu.course.data

import android.util.Log
import androidx.room.withTransaction
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.CourseEntity
import com.xmu.course.data.local.SemesterEntity
import com.xmu.course.data.local.SkippedCourseEntity
import com.xmu.course.data.local.toDomain
import com.xmu.course.data.local.toEntity
import com.xmu.course.data.import.CourseImportContract
import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult
import com.xmu.course.contracts.CourseManagementContract
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Semester
import com.xmu.course.domain.mergeImportedCourses
import com.xmu.course.parser.ParsedCourse
import com.xmu.course.parser.XmuKingosoftParser
import com.xmu.course.parser.XmuParseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

interface SettingsDataSource {
    fun observeSemesters(): Flow<List<Semester>>

    suspend fun updateSemesterStartDate(semesterId: Long, startDate: String)

    suspend fun deleteSemesterCourses(semesterId: Long)

    suspend fun deleteSemester(semesterId: Long)
}

/**
 * 课程/学期数据仓库。
 *
 * 职责边界：查询、导入编排、导入提交、删除；导入编排仍在这里统一协调
 * Parser、课程归一化/合并、冲突查询以及 Room 事务和覆盖写入，不负责 UI。
 *
 * [CourseImportContract] 是迁移期间的 legacy seam。只有当上述导入能力迁移
 * 到稳定的内部 capability、且 provider adapter 不再需要旧 seam 后，才可退休。
 */
class CourseRepository(private val db: AppDatabase) :
    SettingsDataSource,
    CourseImportContract,
    CourseManagementContract {

    private val parser = XmuKingosoftParser()

    /** 观察全部学期（code 降序）。 */
    override fun observeSemesters(): Flow<List<Semester>> =
        db.semesterDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCourses(semesterId: Long): Flow<List<Course>> =
        db.courseDao().observeBySemester(semesterId).map { list ->
            Log.d(TAG, "Room query: semesterId=$semesterId, courses=${list.size}")
            list.map { it.toDomain() }
        }

    suspend fun getSemesters(): List<Semester> =
        db.semesterDao().getAll().map { it.toDomain() }

    /** 原始解析入口（仅供需要警告信息时使用）。 */
    suspend fun parseHtml(html: String): XmuParseResult = parser.parse(html)

    /**
     * 解析 HTML 并判断是否与已有学期冲突。
     * 同 code 学期已存在时返回 [ImportPreparation.Conflict]。
     */
    override suspend fun prepareImport(html: String): ImportPreparation {
        val result = parser.parse(html)
        Log.d(TAG, "parser: ${result.courses.size} 门课程, warnings=${result.warnings.size}")
        val semester = Semester(code = result.semesterCode, name = result.semesterName)
        val courses = mergeImportedCourses(result.courses.map { it.toDomainCourse() })
        if (courses.isEmpty()) {
            throw ImportEmptyException(result.warnings)
        }
        val existing = db.semesterDao().getByCode(semester.code)
        return if (existing == null) {
            ImportPreparation.Ready(semester, courses)
        } else {
            ImportPreparation.Conflict(
                semester = existing.toDomain(),
                existingCount = db.courseDao().countBySemester(existing.id),
                newCount = courses.size,
                pendingCourses = courses,
            )
        }
    }

    /**
     * 提交导入（事务）。
     *
     * @param overwrite 仅在 [ImportPreparation.Conflict] 且用户确认覆盖时为 true；
     * 覆盖会删除该学期全部旧课程后写入新课程（学期记录复用，不重复创建）。
     */
    override suspend fun commitImport(
        semester: Semester,
        courses: List<Course>,
        overwrite: Boolean,
    ): ImportResult {
        val normalizedCourses = mergeImportedCourses(courses)
        val dao = db.semesterDao()
        val result = db.withTransaction {
            val existing = dao.getByCode(semester.code)
            val semesterId = if (existing != null) {
                if (!overwrite) return@withTransaction ImportResult.Cancelled
                val importedStartDate = semester.startDate
                if (importedStartDate != null && importedStartDate != existing.startDate) {
                    dao.updateStartDate(existing.id, importedStartDate)
                    db.timetableDao().updateStartDateBySemesterId(existing.id, importedStartDate)
                }
                existing.id
            } else {
                dao.insert(
                    SemesterEntity(
                        code = semester.code,
                        name = semester.name,
                        startDate = semester.startDate,
                        endDate = semester.endDate,
                    ),
                )
            }
            db.courseDao().deleteBySemester(semesterId)
            db.courseDao().insertAll(normalizedCourses.map { it.toEntity(semesterId) })
            ImportResult.Success(semesterId, normalizedCourses.size)
        }
        if (result is ImportResult.Success) {
            Log.d(TAG, "insert: ${result.count} 门 -> DB count=${result.count} (semesterId=${result.semesterId}, overwrite=$overwrite)")
        }
        return result
    }

    /** 手动添加课程（MANUAL）。 */
    override suspend fun addCourse(semesterId: Long, course: Course) {
        db.courseDao().insert(course.toEntity(semesterId))
    }

    /** 更新课程颜色（用户自定义优先）。 */
    override suspend fun updateCourseColor(courseId: Long, color: String) {
        db.courseDao().updateColor(courseId, color)
    }

    /** 设置学期开始日期（yyyy-MM-dd），用于当前周计算。 */
    override suspend fun updateSemesterStartDate(semesterId: Long, startDate: String) {
        db.withTransaction {
            db.semesterDao().updateStartDate(semesterId, startDate)
            // TimetableViewModel 观察课表表并据此计算 actualWeek；同步更新可立即触发响应式刷新。
            db.timetableDao().updateStartDateBySemesterId(semesterId, startDate)
        }
    }

    /** 更新课程备注。 */
    override suspend fun updateCourseNote(courseId: Long, note: String) {
        db.courseDao().updateNote(courseId, note)
    }

    /** 更新课程名称。 */
    override suspend fun updateCourseName(courseId: Long, name: String) {
        db.courseDao().updateName(courseId, name)
    }

    /** 更新教师。 */
    override suspend fun updateCourseTeacher(courseId: Long, teacher: String) {
        db.courseDao().updateTeacher(courseId, teacher)
    }

    /** 更新地点。 */
    override suspend fun updateCourseLocation(courseId: Long, location: String) {
        db.courseDao().updateLocation(courseId, location)
    }

    /** 删除单门课程（不影响学期/课表）。 */
    override suspend fun deleteCourse(courseId: Long) {
        db.courseDao().deleteById(courseId)
    }

    /** 持续观察全部翘课课程 ID。 */
    override fun observeSkippedCourseIds(): Flow<Set<Long>> =
        db.skippedCourseDao().observeSkippedCourseIds().map { it.toSet() }

    /** 保存课程管理页的翘课多选结果（事务内同步增删）。 */
    override suspend fun saveSkippedCourses(courseIds: Collection<Long>) {
        val desired = courseIds.toSet()
        db.withTransaction {
            val current = db.skippedCourseDao().getSkippedCourseIds().toSet()
            val toDelete = current - desired
            val toInsert = (desired - current).map {
                SkippedCourseEntity(courseId = it, createdAt = System.currentTimeMillis())
            }
            if (toDelete.isNotEmpty()) db.skippedCourseDao().deleteByCourseIds(toDelete.toList())
            if (toInsert.isNotEmpty()) db.skippedCourseDao().insertAll(toInsert)
        }
    }

    /** 课程详情：标记翘课。 */
    suspend fun markCourseSkipped(courseId: Long) {
        db.skippedCourseDao().insert(
            SkippedCourseEntity(courseId = courseId, createdAt = System.currentTimeMillis()),
        )
    }

    /** 课程详情：取消翘课。 */
    suspend fun unmarkCourseSkipped(courseId: Long) {
        db.skippedCourseDao().deleteByCourseId(courseId)
    }

    /** 按课表观察全部课程（课程管理页 / 后续 Widget 用）。 */
    override fun observeCoursesByTimetable(timetableId: Long): Flow<List<Course>> =
        db.courseDao().observeByTimetableId(timetableId).map { list -> list.map { it.toDomain() } }

    /** 按课表观察指定日期课程（LocalDate → 星期一=1 … 星期日=7，后续提醒用）。 */
    fun observeTodayCourses(timetableId: Long, date: LocalDate): Flow<List<Course>> =
        db.courseDao().observeByTimetableAndDay(timetableId, date.dayOfWeek.value)
            .map { list -> list.map { it.toDomain() } }

    /** 仅删除某学期课程（保留学期记录）。 */
    override suspend fun deleteSemesterCourses(semesterId: Long) {
        db.courseDao().deleteBySemester(semesterId)
    }

    override suspend fun deleteSemester(semesterId: Long) {
        db.withTransaction {
            db.courseDao().deleteBySemester(semesterId)
            db.semesterDao().deleteById(semesterId)
        }
    }

    private fun ParsedCourse.toDomainCourse(): Course = Course(
        name = name,
        teacher = teacher,
        location = location,
        dayOfWeek = dayOfWeek,
        startSection = startSection,
        duration = duration,
        weeks = weeks,
        source = CourseSource.IMPORT,
        color = autoColor(name),
    )

    companion object {
        private const val TAG = "XmuImport"

        /** 按课程名稳定分配卡片颜色（超级课程表风格的多彩卡片）。 */
        private val PALETTE = listOf(
            "#FAAC8F", "#FDCF93", "#93D36E", "#7FD4E0",
            "#A79FE1", "#F49BC1", "#8FBCFA", "#E8C877",
        )

        fun autoColor(name: String): String =
            PALETTE[((name.hashCode() % PALETTE.size) + PALETTE.size) % PALETTE.size]
    }
}

/** 解析结果为空时抛出，消息中携带解析 warnings。 */
class ImportEmptyException(val warnings: List<String>) :
    RuntimeException("解析结果为空：${warnings.joinToString("; ")}")





