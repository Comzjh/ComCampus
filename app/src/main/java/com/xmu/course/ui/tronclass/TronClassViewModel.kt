package com.xmu.course.ui.tronclass

import android.app.Application
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xmu.course.ui.OneShotToken
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.data.tronclass.assignment.DisabledAssignmentSyncSettings
import com.xmu.course.data.tronclass.assignment.SharedPreferencesAssignmentSyncSettings
import com.xmu.course.data.tronclass.feature.TronClassAuthUiState
import com.xmu.course.data.tronclass.feature.TronClassCourseSyncUiResult
import com.xmu.course.data.tronclass.feature.TronClassFeatureRepository
import com.xmu.course.data.tronclass.feature.TronClassLogoutUiResult
import com.xmu.course.data.tronclass.feature.TronClassTodoSyncUiResult
import com.xmu.course.data.tronclass.model.TronClassUiError
import com.xmu.course.data.tronclass.model.TronClassUiErrorCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TronClassViewModel(
    private val repository: TronClassFeatureRepository,
    private val assignmentSyncSettings: AssignmentSyncSettings = DisabledAssignmentSyncSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TronClassUiState())
    val uiState: StateFlow<TronClassUiState> = _uiState.asStateFlow()
    private var syncJob: Job? = null
    private val syncCompletionClaim = OneShotToken()

    /**
     * 消费"同步成功"事件：同一令牌只有第一次调用返回 true。
     *
     * 只在真实成功时递增，失败/会话过期永不置位，因此不会假成功跳转。
     */
    fun claimSyncCompletion(): Boolean = syncCompletionClaim.claim(_uiState.value.syncCompletionToken)

    init {
        viewModelScope.launch {
            repository.observeCourses()
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            screenState = TronClassScreenState.Error,
                            error = storageError(),
                        )
                    }
                }
                .collect { courses ->
                    _uiState.update { state ->
                        state.copy(
                            courses = courses,
                            lastSyncTime = state.lastSyncTime,
                        )
                    }
                }
        }
        refreshSession()
    }

    fun refreshSession() {
        _uiState.update { it.copy(screenState = TronClassScreenState.CheckingSession, error = null) }
        viewModelScope.launch {
            val authenticated = repository.refreshSession()
            _uiState.update { state ->
                state.copy(
                    screenState = when (authenticated) {
                        TronClassAuthUiState.AUTHENTICATED -> TronClassScreenState.Authenticated
                        TronClassAuthUiState.UNAUTHENTICATED -> TronClassScreenState.Unauthenticated
                        TronClassAuthUiState.SESSION_EXPIRED -> TronClassScreenState.SessionExpired
                    },
                    error = null,
                )
            }
        }
    }

    fun onAuthResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            refreshSession()
        } else {
            _uiState.update { it.copy(screenState = TronClassScreenState.Unauthenticated) }
        }
    }

    fun syncCourses() {
        if (syncJob?.isActive == true) return
        _uiState.update {
            it.copy(screenState = TronClassScreenState.Syncing, error = null)
        }
        syncJob = viewModelScope.launch {
            when (val result = repository.syncCourses()) {
                TronClassCourseSyncUiResult.SessionExpired -> _uiState.update {
                    it.copy(screenState = TronClassScreenState.SessionExpired, error = null)
                }
                is TronClassCourseSyncUiResult.Success -> syncAssignmentsIfEnabled(
                    courseCount = result.count,
                    lastSyncTime = result.lastSyncTime,
                )
                is TronClassCourseSyncUiResult.Failed -> _uiState.update {
                    it.copy(screenState = TronClassScreenState.Error, error = result.error)
                }
            }
        }
    }

    private suspend fun syncAssignmentsIfEnabled(courseCount: Int, lastSyncTime: Long?) {
        if (!assignmentSyncSettings.isAutoImportEnabled()) {
            _uiState.update {
                it.copy(
                    screenState = TronClassScreenState.Success,
                    lastSyncCount = courseCount,
                    lastSyncTime = lastSyncTime ?: it.lastSyncTime,
                    lastAssignmentSyncCount = null,
                    error = null,
                    syncCompletionToken = it.syncCompletionToken + 1,
                )
            }
            return
        }
        when (val assignmentResult = repository.syncTodoSources()) {
            is TronClassTodoSyncUiResult.Success -> _uiState.update {
                it.copy(
                    screenState = TronClassScreenState.Success,
                    lastSyncCount = courseCount,
                    lastSyncTime = lastSyncTime ?: it.lastSyncTime,
                    lastAssignmentSyncCount = assignmentResult.count,
                    error = null,
                    syncCompletionToken = it.syncCompletionToken + 1,
                )
            }
            TronClassTodoSyncUiResult.SessionExpired -> {
                _uiState.update { it.copy(screenState = TronClassScreenState.SessionExpired, error = null) }
            }
            is TronClassTodoSyncUiResult.Failed -> {
                _uiState.update {
                    it.copy(screenState = TronClassScreenState.Error, error = assignmentResult.error)
                }
            }
        }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutConfirmation = true) }
    }

    fun cancelLogout() {
        _uiState.update { it.copy(showLogoutConfirmation = false) }
    }

    fun confirmLogout() {
        _uiState.update { it.copy(showLogoutConfirmation = false) }
        viewModelScope.launch {
            when (val result = repository.logout()) {
                TronClassLogoutUiResult.Success -> _uiState.update {
                    it.copy(
                        screenState = TronClassScreenState.Unauthenticated,
                        courses = emptyList(),
                        lastSyncTime = null,
                        lastSyncCount = null,
                        lastAssignmentSyncCount = null,
                        error = null,
                        clearWebDataRequested = true,
                    )
                }
                is TronClassLogoutUiResult.Failed -> _uiState.update {
                    it.copy(screenState = TronClassScreenState.Error, error = result.error)
                }
            }
        }
    }

    fun consumeClearWebDataRequest() {
        _uiState.update { it.copy(clearWebDataRequested = false) }
    }

    fun onWebDataCleared(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            _uiState.update {
                it.copy(
                    screenState = TronClassScreenState.Error,
                    error = storageError(),
                )
            }
        }
    }
}

private fun storageError() = TronClassUiError(TronClassUiErrorCategory.StorageFailure)

class TronClassViewModelFactory(
    private val repository: TronClassFeatureRepository,
    private val assignmentSyncSettings: AssignmentSyncSettings = DisabledAssignmentSyncSettings,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TronClassViewModel::class.java))
        return TronClassViewModel(repository, assignmentSyncSettings) as T
    }

    companion object {
        fun fromApplication(application: Application): TronClassViewModelFactory {
            val app = application as? XmuCourseApplication
                ?: error("TronClassViewModel requires XmuCourseApplication")
            val context = application.applicationContext
            val tron = app.appContainer.tronClass
            return TronClassViewModelFactory(
                tron.featureRepository,
                SharedPreferencesAssignmentSyncSettings(context),
            )
        }
    }
}
