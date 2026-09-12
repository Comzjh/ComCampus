package com.xmu.course.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.DisplaySettings
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** 课表显示设置页 UI 状态。 */
data class TimetableSettingsUiState(
    val timetableName: String? = null,
    val config: TimetableConfig? = null,
    /** 网格线是轻量全局开关；入口统一收进课表设置页。 */
    val showGrid: Boolean = true,
)

/**
 * 课表显示设置 ViewModel：每课表独立配置 + 全局网格线入口。
 *
 * 数据流：TimetablePrefs.currentTimetableId → observeConfig(Flow) → StateFlow；
 * 修改走 Repository.updateConfig → Room → Flow 自动刷新课表页。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val timetableRepo = TimetableRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(TimetableSettingsUiState())
    val uiState: StateFlow<TimetableSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            TimetablePrefs.currentTimetableId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(TimetableSettingsUiState())
                } else {
                    combine(
                        timetableRepo.observeTimetable(id),
                        timetableRepo.observeConfig(id),
                        DisplaySettings.showGrid,
                    ) { timetable, config, showGrid ->
                        TimetableSettingsUiState(
                            timetableName = timetable?.name,
                            config = config,
                            showGrid = showGrid,
                        )
                    }
                }
            }.collect { newState -> _uiState.value = newState }
        }
    }

    /** 修改配置（copy 组装整份保存，Room Flow 自动刷新课表页）。 */
    fun update(transform: (TimetableConfig) -> TimetableConfig) {
        val config = _uiState.value.config ?: return
        viewModelScope.launch { timetableRepo.updateConfig(transform(config)) }
    }

    /** 修改全局网格线开关（保持原 DisplaySettings 存储，避免不必要数据库迁移）。 */
    fun setShowGrid(value: Boolean) {
        DisplaySettings.setShowGrid(getApplication(), value)
    }
}
