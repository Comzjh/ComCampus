package com.xmu.course.ui.timetable

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.ViewWeekPreference
import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.data.DisplaySettings
import com.xmu.course.data.TimetableAxisStyle
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

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
    /** 已同步畅课课程与本地课程的运行时关联，不写入本地课表表结构。 */
    val tronCourseLinks: Map<Long, TimetableMatchModel> = emptyMap(),
)

/**
 * 课表 ViewModel。
 *
 * 数据流（全响应式）：
 * TimetableFeatureRepository.observeCurrentTimetableState + 显示设置
 * → StateFlow → Compose。
 */
class TimetableViewModel(
    application: Application,
    private val repository: TimetableFeatureRepository,
    private val viewWeekPreference: ViewWeekPreference,
    /** 时间轴文字样式：全局显示偏好，由组合根注入；默认用于测试。 */
    val axisStyle: StateFlow<TimetableAxisStyle> = MutableStateFlow(TimetableAxisStyle()),
    private val clock: Clock = Clock.systemDefaultZone(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()
    private var dateBoundaryJob: Job? = null


    init {
        viewModelScope.launch {
            combine(
                repository.observeCurrentTimetableState(),
                DisplaySettings.showGrid,
            ) { featureState, showGrid ->
                val timetable = featureState.timetable
                if (timetable == null) {
                    TimetableUiState(isLoading = false, hasData = false, showGrid = showGrid)
                } else {
                    val actualWeek = computeCurrentWeek(timetable, LocalDate.now(clock))
                        .coerceIn(1, timetable.totalWeeks)
                    val viewWeek = resolveViewWeek(
                        previousTimetableId = _uiState.value.timetable?.id,
                        previousViewWeek = _uiState.value.viewWeek,
                        timetableId = timetable.id,
                        savedViewWeek = viewWeekPreference.getViewWeek(timetable.id),
                        actualWeek = actualWeek,
                        totalWeeks = timetable.totalWeeks,
                    )
                    TimetableUiState(
                        timetable = timetable,
                        courses = featureState.courses,
                        skippedCourseIds = featureState.skippedCourseIds,
                        config = featureState.config,
                        actualWeek = actualWeek,
                        viewWeek = viewWeek,
                        totalWeeks = timetable.totalWeeks,
                        isLoading = false,
                        hasData = true,
                        showGrid = showGrid,
                        tronCourseLinks = featureState.timetableLinks,
                    )
                }
            }
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }

    /** App 恢复时立即重算实际周，并等待前台后续的本地日期边界。 */
    fun onForeground() {
        refreshActualWeek()
        dateBoundaryJob?.cancel()
        dateBoundaryJob = viewModelScope.launch {
            while (isActive) {
                delay(millisUntilNextLocalMidnight(clock))
                refreshActualWeek()
            }
        }
    }

    /** App 进入后台后停止日界等待；恢复时会按当前时钟重新计算。 */
    fun onBackground() {
        dateBoundaryJob?.cancel()
        dateBoundaryJob = null
    }

    private fun refreshActualWeek(today: LocalDate = LocalDate.now(clock)) {
        _uiState.update { state ->
            val timetable = state.timetable ?: return@update state
            val actualWeek = computeCurrentWeek(timetable, today)
            if (actualWeek == state.actualWeek) state else state.copy(actualWeek = actualWeek)
        }
    }

    /** 切换查看周（仅影响浏览，不改 actualWeek），并按课表持久化。 */
    fun selectWeek(week: Int) {
        val coerced = week.coerceIn(1, _uiState.value.totalWeeks)
        _uiState.update { it.copy(viewWeek = coerced) }
        val timetable = _uiState.value.timetable
        if (timetable != null) {
            viewWeekPreference.setViewWeek(timetable.id, coerced)
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
        viewModelScope.launch { repository.addCourse(course) }
    }

    /** 用户修改课程颜色。 */
    fun updateCourseColor(courseId: Long, color: String) {
        viewModelScope.launch { repository.updateCourseColor(courseId, color) }
    }

    /** 保存课程备注。 */
    fun updateCourseNote(courseId: Long, note: String) {
        viewModelScope.launch { repository.updateCourseNote(courseId, note) }
    }

    /** 课程详情：标记 / 取消翘课。 */
    fun toggleCourseSkipped(courseId: Long, skipped: Boolean) {
        viewModelScope.launch { repository.setCourseSkipped(courseId, skipped) }
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
            return TimetableCalendar.currentWeek(timetable, today)
        }

        internal fun millisUntilNextLocalMidnight(clock: Clock): Long {
            val now = ZonedDateTime.now(clock)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
            return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
        }
    }
}

/** 从 Application 的组合根创建课表 ViewModel。 */
class TimetableViewModelFactory(
    private val application: Application,
    private val repository: TimetableFeatureRepository,
    private val viewWeekPreference: ViewWeekPreference,
    private val axisStyle: StateFlow<TimetableAxisStyle>,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TimetableViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return TimetableViewModel(application, repository, viewWeekPreference, axisStyle) as T
    }

    companion object {
        fun fromApplication(application: Application): TimetableViewModelFactory {
            val app = application as? com.xmu.course.XmuCourseApplication
                ?: error("TimetableViewModel requires XmuCourseApplication")
            return TimetableViewModelFactory(
                application = application,
                repository = app.appContainer.timetableFeatureRepository,
                viewWeekPreference = app.appContainer.timetableViewWeekPreference,
                axisStyle = app.appContainer.timetableAxisStyle,
            )
        }
    }
}
