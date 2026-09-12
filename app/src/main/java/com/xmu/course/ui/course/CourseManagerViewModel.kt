package com.xmu.course.ui.course

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 课程管理页 UI 状态。 */
data class CourseManagerUiState(
    val timetable: Timetable? = null,
    val courses: List<Course> = emptyList(),
    val skippedCourseIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

/**
 * 课程管理 ViewModel。
 *
 * 数据流：TimetablePrefs.currentTimetableId → 课表(Flow) + 课程(Flow) → StateFlow → Compose。
 * 所有课程修改统一走 CourseRepository，UI 不直接操作 DAO。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourseManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val courseRepo = CourseRepository(AppDatabase.getInstance(application))
    private val timetableRepo = TimetableRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(CourseManagerUiState())
    val uiState: StateFlow<CourseManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            TimetablePrefs.currentTimetableId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(CourseManagerUiState(isLoading = false))
                } else {
                    combine(
                        timetableRepo.observeTimetable(id),
                        courseRepo.observeCoursesByTimetable(id),
                        courseRepo.observeSkippedCourseIds(),
                    ) { timetable, courses, skipped ->
                        CourseManagerUiState(
                            timetable = timetable,
                            courses = courses,
                            skippedCourseIds = skipped,
                            isLoading = false,
                        )
                    }
                }
            }.collect { newState -> _uiState.value = newState }
        }
    }

    /** 手动添加课程（归属当前课表）。 */
    fun addCourse(course: Course) {
        val timetable = _uiState.value.timetable ?: run {
            _uiState.update { it.copy(message = "当前没有课表") }
            return
        }
        viewModelScope.launch {
            courseRepo.addCourse(timetable.semesterId, course)
            _uiState.update { it.copy(message = "已添加：${course.name}") }
        }
    }

    fun updateCourseName(courseId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(message = "课程名称不能为空") }
            return
        }
        viewModelScope.launch {
            courseRepo.updateCourseName(courseId, trimmed)
            _uiState.update { it.copy(message = "已保存") }
        }
    }

    fun updateCourseTeacher(courseId: Long, teacher: String) {
        viewModelScope.launch { courseRepo.updateCourseTeacher(courseId, teacher.trim()) }
    }

    fun updateCourseLocation(courseId: Long, location: String) {
        viewModelScope.launch { courseRepo.updateCourseLocation(courseId, location.trim()) }
    }

    fun updateCourseNote(courseId: Long, note: String) {
        viewModelScope.launch { courseRepo.updateCourseNote(courseId, note.trim()) }
    }

    fun updateCourseColor(courseId: Long, color: String) {
        viewModelScope.launch { courseRepo.updateCourseColor(courseId, color) }
    }

    /** 保存翘课设置：整批覆盖当前选中课程。 */
    fun saveSkippedCourses(courseIds: Set<Long>) {
        viewModelScope.launch {
            courseRepo.saveSkippedCourses(courseIds)
            _uiState.update { it.copy(message = "翘课设置已保存") }
        }
    }

    /** 清空当前课表全部课程（二次确认后调用）。 */
    fun clearAllCourses() {
        val timetable = _uiState.value.timetable ?: return
        viewModelScope.launch {
            courseRepo.deleteSemesterCourses(timetable.semesterId)
            _uiState.update { it.copy(message = "已清空全部课程") }
        }
    }

    /** 删除单门课程（仅 Course，不影响学期/课表）。 */
    fun deleteCourse(courseId: Long) {
        viewModelScope.launch {
            courseRepo.deleteCourse(courseId)
            _uiState.update { it.copy(message = "已删除课程") }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
