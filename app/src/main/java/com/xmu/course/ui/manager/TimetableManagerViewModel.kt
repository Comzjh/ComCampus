package com.xmu.course.ui.manager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.TimetableWithCount
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 课表管理页 UI 状态。 */
data class TimetableManagerUiState(
    val timetables: List<TimetableWithCount> = emptyList(),
    val currentId: Long? = null,
    val message: String? = null,
)

/**
 * 课表管理 ViewModel：列表 / 切换 / 重命名 / 删除 / 新建。
 */
class TimetableManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TimetableRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(TimetableManagerUiState())
    val uiState: StateFlow<TimetableManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeTimetables().collect { list ->
                val prefsId = TimetablePrefs.currentTimetableId.value
                val currentId =
                    if (list.any { it.timetable.id == prefsId }) prefsId
                    else list.firstOrNull()?.timetable?.id
                _uiState.update { it.copy(timetables = list, currentId = currentId) }
            }
        }
    }

    /** 切换当前课表。 */
    fun select(id: Long?) {
        TimetablePrefs.setCurrent(getApplication(), id)
        _uiState.update { it.copy(currentId = id) }
    }

    /** 创建空课表（首个课表自动设为当前）。 */
    fun createTimetable(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(message = "课表名称不能为空") }
            return
        }
        viewModelScope.launch {
            val created = repo.createTimetable(trimmed)
            if (_uiState.value.currentId == null) select(created.id)
            _uiState.update { it.copy(message = "已创建课表：$trimmed") }
        }
    }

    fun renameTimetable(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.rename(id, trimmed)
            _uiState.update { it.copy(message = "已重命名") }
        }
    }

    /**
     * 删除课表：自定义课表级联删除学期与课程；导入课表仅删除课表记录。
     * 删除当前课表后自动回退到剩余第一张。
     */
    fun deleteTimetable(id: Long) {
        viewModelScope.launch {
            val wasCurrent = _uiState.value.currentId == id
            repo.deleteTimetable(id)
            if (wasCurrent) {
                val remaining = repo.observeTimetables().first()
                select(remaining.firstOrNull()?.timetable?.id)
            }
            _uiState.update { it.copy(message = "已删除课表") }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
