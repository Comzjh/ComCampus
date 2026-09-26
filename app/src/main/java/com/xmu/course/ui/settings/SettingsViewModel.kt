package com.xmu.course.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.SettingsDataSource
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.contracts.presentation.StartupDestinationPreference
import com.xmu.course.domain.Semester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val semester: Semester? = null,
    val message: String? = null,
    val autoImportAssignments: Boolean = false,
    val autoRefreshTodo: Boolean = true,
    val startupDestination: StartupDestination = StartupDestination.HOME,
)

/** 设置页 ViewModel：学期信息 / 开学日期 / 删除课表。 */
class SettingsViewModel(
    application: Application,
    private val repository: SettingsDataSource,
    private val assignmentSyncSettings: AssignmentSyncSettings,
    private val todoAutoSyncSettings: TodoAutoSyncSettings,
    private val startupDestinationPreference: StartupDestinationPreference,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        (application as? XmuCourseApplication)?.appContainer?.settings?.repository
            ?: error("SettingsViewModel requires XmuCourseApplication"),
        (application as? XmuCourseApplication)?.appContainer?.settings?.assignmentSyncSettings
            ?: error("SettingsViewModel requires XmuCourseApplication"),
        (application as? XmuCourseApplication)?.appContainer?.settings?.todoAutoSyncSettings
            ?: error("SettingsViewModel requires XmuCourseApplication"),
        (application as? XmuCourseApplication)?.appContainer?.startupDestinationPreference
            ?: error("SettingsViewModel requires XmuCourseApplication"),
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            autoImportAssignments = assignmentSyncSettings.isAutoImportEnabled(),
            autoRefreshTodo = todoAutoSyncSettings.isEnabled(),
            startupDestination = startupDestinationPreference.destination() ?: StartupDestination.HOME,
        )
    }

    init {
        viewModelScope.launch {
            repository.observeSemesters()
                .catch { _uiState.update { it.copy(semester = null) } }
                .collect { semesters ->
                    // 排除自定义课表使用的隐藏学期（code = custom-*）。
                    val semester = semesters
                        .filterNot { it.code.startsWith("custom-") }
                        .maxByOrNull { it.code }
                    _uiState.update { it.copy(semester = semester) }
                }
        }
    }

    /** 保存开学日期（yyyy-MM-dd），使课表页可自动定位当前周。 */
    fun saveStartDate(date: String) {
        val semester = _uiState.value.semester ?: return
        viewModelScope.launch {
            repository.updateSemesterStartDate(semester.id, date)
            _uiState.update { it.copy(message = "开学日期已保存：$date") }
        }
    }

    /**
     * 删除课表。
     * @param deleteSemester false=只删课程；true=连同学期记录一起删除。
     */
    fun deleteTimetable(deleteSemester: Boolean) {
        val semester = _uiState.value.semester ?: return
        viewModelScope.launch {
            if (deleteSemester) {
                repository.deleteSemester(semester.id)
                _uiState.update { it.copy(message = "已删除学期及全部课程") }
            } else {
                repository.deleteSemesterCourses(semester.id)
                _uiState.update { it.copy(message = "已删除课程（学期记录保留）") }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun setAutoImportAssignments(enabled: Boolean) {
        assignmentSyncSettings.setAutoImportEnabled(enabled)
        _uiState.update { it.copy(autoImportAssignments = enabled) }
    }

    fun setAutoRefreshTodo(enabled: Boolean) {
        todoAutoSyncSettings.setEnabled(enabled)
        _uiState.update { it.copy(autoRefreshTodo = enabled) }
    }

    /** 设置冷启动默认打开页面（首页或课表）；用户偏好，不进 Room。 */
    fun setStartupDestination(destination: StartupDestination) {
        startupDestinationPreference.setDestination(destination)
        _uiState.update { it.copy(startupDestination = destination) }
    }
}
