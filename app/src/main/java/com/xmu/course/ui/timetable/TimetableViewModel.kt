package com.xmu.course.ui.timetable

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.DisplaySettings
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 课表页 UI 状态。 */
data class TimetableUiState(
    val timetable: Timetable? = null,
    val courses: List<Course> = emptyList(),
    /** 本地“翘课”课程 ID；只影响 CourseCard 视觉。 */
    val skippedCourseIds: Set<Long> = emptySet(),
    /** 每课表独立的显示配置（Room 实时观察）。 */
    val config: TimetableConfig = TimetableConfig(timetableId = 0L),
    /** 实际教学周：startDate + 今天自动计算，用户查看操作不会改变它。 */
    val actualWeek: Int = 1,
    /** 查看周：用户当前浏览的周，默认等于 actualWeek，可手动切换并按课表持久化。 */
    val viewWeek: Int = 1,
    val totalWeeks: Int = 25,
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    /** 网格线为全局设置（不属于课表配置）。 */
    val showGrid: Boolean = true,
)

/** combine 组包：全局网格线 + prefs 当前课表 ID。 */
private data class UiSources(
    val showGrid: Boolean,
    val prefsTimetableId: Long?,
)

/**
 * 课表 ViewModel。
 *
 * 数据流（全响应式）：
 * 显示设置 + TimetablePrefs.currentTimetableId
 * → TimetableRepository.observeTimetable
 * → combine(observeCourses, observeConfig)
 * → StateFlow → Compose。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    // 注意：保持 (Application) 单参构造，viewModel() 的 AndroidViewModelFactory 依赖反射创建；
    // Phase 4 曾加过带默认值的 db 参数导致闪退（NoSuchMethodException），勿改回。
    private val db = AppDatabase.getInstance(application)
    private val courseRepo = CourseRepository(db)
    private val timetableRepo = TimetableRepository(db)

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                DisplaySettings.showGrid,
                TimetablePrefs.currentTimetableId,
            ) { showGrid, prefsId -> UiSources(showGrid, prefsId) }
                .flatMapLatest { (showGrid, prefsId) ->
                    timetableRepo.observeTimetables().flatMapLatest { timetables ->
                        if (timetables.isEmpty()) {
                            flowOf(
                                TimetableUiState(
                                    isLoading = false, hasData = false, showGrid = showGrid,
                                ),
                            )
                        } else {
                            // prefs 无效（未设置/课表已删）→ 回退第一张并写回 prefs。
                            val chosen = timetables.firstOrNull { it.timetable.id == prefsId }?.timetable
                                ?: timetables.first().timetable
                            if (chosen.id != prefsId) {
                                TimetablePrefs.setCurrent(getApplication(), chosen.id)
                            }
                            timetableRepo.observeTimetable(chosen.id).flatMapLatest { tt ->
                                val timetable = tt ?: chosen
                                combine(
                                    courseRepo.observeCourses(timetable.semesterId),
                                    timetableRepo.observeConfig(timetable.id),
                                    courseRepo.observeSkippedCourseIds(),
                                ) { courses, config, skipped ->
                                    val actualWeek = computeCurrentWeek(timetable)
                                        .coerceIn(1, timetable.totalWeeks)
                                    val viewWeek = resolveViewWeek(
                                        previousTimetableId = _uiState.value.timetable?.id,
                                        previousViewWeek = _uiState.value.viewWeek,
                                        timetableId = timetable.id,
                                        savedViewWeek = TimetablePrefs.getViewWeek(
                                            getApplication(), timetable.id,
                                        ),
                                        actualWeek = actualWeek,
                                        totalWeeks = timetable.totalWeeks,
                                    )
                                    TimetableUiState(
                                        timetable = timetable,
                                        courses = courses,
                                        skippedCourseIds = skipped,
                                        config = config,
                                        actualWeek = actualWeek,
                                        viewWeek = viewWeek,
                                        totalWeeks = timetable.totalWeeks,
                                        isLoading = false,
                                        hasData = true,
                                        showGrid = showGrid,
                                    )
                                }
                            }
                        }
                    }
                }
                .collect { newState ->
                    Log.d(
                        "XmuImport",
                        "ViewModel emit: timetable=${newState.timetable?.name}/id=${newState.timetable?.id}, " +
                            "actual=${newState.actualWeek}, view=${newState.viewWeek}, courses=${newState.courses.size}",
                    )
                    _uiState.value = newState
                }
        }
    }

    /** 切换查看周（仅影响浏览，不改 actualWeek），并按课表持久化。 */
    fun selectWeek(week: Int) {
        val coerced = week.coerceIn(1, _uiState.value.totalWeeks)
        _uiState.update { it.copy(viewWeek = coerced) }
        val timetable = _uiState.value.timetable
        if (timetable != null) {
            TimetablePrefs.setViewWeek(getApplication(), timetable.id, coerced)
        }
    }

    /** 回到本周：viewWeek = actualWeek。 */
    fun backToCurrentWeek() = selectWeek(_uiState.value.actualWeek)

    /** 手动添加课程（写入当前课表对应的学期）。 */
    fun addCourse(course: Course) {
        val timetable = _uiState.value.timetable
        if (timetable == null) {
            Log.w("XmuImport", "addCourse: 无当前课表，忽略")
            return
        }
        viewModelScope.launch { courseRepo.addCourse(timetable.semesterId, course) }
    }

    /** 用户修改课程颜色。 */
    fun updateCourseColor(courseId: Long, color: String) {
        viewModelScope.launch { courseRepo.updateCourseColor(courseId, color) }
    }

    /** 保存课程备注。 */
    fun updateCourseNote(courseId: Long, note: String) {
        viewModelScope.launch { courseRepo.updateCourseNote(courseId, note) }
    }

    /** 课程详情：标记 / 取消翘课。 */
    fun toggleCourseSkipped(courseId: Long, skipped: Boolean) {
        viewModelScope.launch {
            if (skipped) courseRepo.markCourseSkipped(courseId)
            else courseRepo.unmarkCourseSkipped(courseId)
        }
    }

    companion object {
        /**
         * 解析查看周：
         * - 同一课表：保持用户当前浏览位置（不回跳）；
         * - 换课表：恢复该课表保存的查看周，未保存过则回退实际周。
         */
        fun resolveViewWeek(
            previousTimetableId: Long?,
            previousViewWeek: Int,
            timetableId: Long,
            savedViewWeek: Int?,
            actualWeek: Int,
            totalWeeks: Int,
        ): Int =
            if (previousTimetableId == timetableId) {
                previousViewWeek.coerceIn(1, totalWeeks)
            } else {
                (savedViewWeek ?: actualWeek).coerceIn(1, totalWeeks)
            }

        /** 根据课表开始日期计算教学周；startDate 未设置时返回 1。 */
        fun computeCurrentWeek(timetable: Timetable, today: LocalDate = LocalDate.now()): Int {
            val start = timetable.startDate?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            } ?: return 1
            if (today.isBefore(start)) return 1
            val days = ChronoUnit.DAYS.between(start, today)
            return ((days / 7) + 1).toInt()
        }
    }
}
