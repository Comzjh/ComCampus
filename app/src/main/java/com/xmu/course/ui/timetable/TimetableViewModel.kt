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
    /** 查看周：只在本次前台会话内保留；重新打开时归位 actualWeek。 */
    val viewWeek: Int = 1,
    val totalWeeks: Int = 25,
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    /** 网格线为全局设置（不属于课表配置）。 */
    val showGrid: Boolean = true,
    /** 已同步畅课课程与本地课程的运行时关联，不写入本地课表表结构。 */
    val tronCourseLinks: Map<Long, TimetableMatchModel> = emptyMap(),
    /** 每次重新进入前台都请求 Pager 定位，即使目标周与上次状态相同。 */
    val weekResetGeneration: Long = 0L,
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
    // 保留组合根的构造接口；跨会话保存的查看周不再参与浏览状态。
    @Suppress("UNUSED_PARAMETER") viewWeekPreference: ViewWeekPreference,
    /** 时间轴文字样式：全局显示偏好，由组合根注入；默认用于测试。 */
    val axisStyle: StateFlow<TimetableAxisStyle> = MutableStateFlow(TimetableAxisStyle()),
    private val clock: Clock = Clock.systemDefaultZone(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()
    private var dateBoundaryJob: Job? = null
    private var isForeground = false
    private val sessionViewWeeks = mutableMapOf<Long, Int>()


    init {
        viewModelScope.launch {
            combine(
                repository.observeCurrentTimetableState(),
                DisplaySettings.showGrid,
            ) { featureState, showGrid ->
                val previous = _uiState.value
                val timetable = featureState.timetable
                if (timetable == null) {
                    TimetableUiState(
                        isLoading = false,
                        hasData = false,
                        showGrid = showGrid,
                        weekResetGeneration = previous.weekResetGeneration,
                    )
                } else {
                    val actualWeek = computeCurrentWeek(timetable, LocalDate.now(clock))
                        .coerceIn(1, timetable.totalWeeks)
                    val viewWeek = resolveViewWeek(
                        previousTimetableId = previous.timetable?.id,
                        previousViewWeek = previous.viewWeek,
                        timetableId = timetable.id,
                        sessionViewWeek = sessionViewWeeks[timetable.id],
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
                        weekResetGeneration = previous.weekResetGeneration,
                    )
                }
            }
                .collect { newState ->
                    newState.timetable?.let { sessionViewWeeks[it.id] = newState.viewWeek }
                    _uiState.value = newState
                }
        }
    }

    /** 新的前台会话归位本周；重复的前台通知不打断用户浏览。 */
    fun onForeground() {
        if (isForeground) return
        isForeground = true
        sessionViewWeeks.clear()
        refreshActualWeek(resetViewWeek = true)
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
        isForeground = false
        dateBoundaryJob?.cancel()
        dateBoundaryJob = null
    }

    private fun refreshActualWeek(
        today: LocalDate = LocalDate.now(clock),
        resetViewWeek: Boolean = false,
    ) {
        _uiState.update { state ->
            val actualWeek = state.timetable?.let { computeCurrentWeek(it, today) } ?: state.actualWeek
            state.copy(
                actualWeek = actualWeek,
                viewWeek = if (resetViewWeek) actualWeek else state.viewWeek,
                weekResetGeneration = state.weekResetGeneration + if (resetViewWeek) 1L else 0L,
            )
        }
        if (resetViewWeek) {
            _uiState.value.timetable?.let { sessionViewWeeks[it.id] = _uiState.value.viewWeek }
        }
    }

    /** 切换查看周只保存在本次前台会话，不改 actualWeek 或跨会话偏好。 */
    fun selectWeek(week: Int) {
        val state = _uiState.value
        val timetable = state.timetable ?: return
        val coerced = week.coerceIn(1, state.totalWeeks)
        sessionViewWeeks[timetable.id] = coerced
        _uiState.update { it.copy(viewWeek = coerced) }
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
         * - 换课表：恢复本次会话中该课表的浏览位置，首次查看则使用实际周。
         */
        fun resolveViewWeek(
            previousTimetableId: Long?,
            previousViewWeek: Int,
            timetableId: Long,
            sessionViewWeek: Int?,
            actualWeek: Int,
            totalWeeks: Int,
        ): Int =
            if (previousTimetableId == timetableId) {
                previousViewWeek.coerceIn(1, totalWeeks)
            } else {
                (sessionViewWeek ?: actualWeek).coerceIn(1, totalWeeks)
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
