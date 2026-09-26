package com.xmu.course.ui.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.auth.AuthServiceState
import com.xmu.course.data.auth.AuthStatus
import com.xmu.course.data.auth.AuthStatusSource
import com.xmu.course.data.auth.SharedPreferencesWiseduSessionMarker
import com.xmu.course.data.auth.TronClassAuthStatusSource
import com.xmu.course.data.auth.WiseduAuthStatusSource
import com.xmu.course.data.auth.WiseduAuthObservation
import com.xmu.course.data.auth.WiseduAuthStatusController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthCenterUiState(
    val wisedu: AuthServiceState = AuthServiceState(
        service = com.xmu.course.data.auth.AuthService.WISEDU,
        status = AuthStatus.CHECKING,
    ),
    val tronClass: AuthServiceState = AuthServiceState(
        service = com.xmu.course.data.auth.AuthService.TRONCLASS,
        status = AuthStatus.CHECKING,
    ),
    val refreshing: Boolean = true,
)

class AuthCenterViewModel(
    private val wiseduSource: WiseduAuthStatusController,
    private val tronClassSource: AuthStatusSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthCenterUiState())
    val uiState: StateFlow<AuthCenterUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _uiState.update {
            it.copy(
                wisedu = it.wisedu.copy(status = AuthStatus.CHECKING),
                tronClass = it.tronClass.copy(status = AuthStatus.CHECKING),
                refreshing = true,
            )
        }
        refreshJob = viewModelScope.launch {
            val wiseduStatus = runCatching { wiseduSource.check() }.getOrDefault(AuthStatus.UNKNOWN)
            val tronClassStatus = runCatching { tronClassSource.check() }.getOrDefault(AuthStatus.UNKNOWN)
            _uiState.update {
                it.copy(
                    wisedu = it.wisedu.copy(status = wiseduStatus),
                    tronClass = it.tronClass.copy(status = tronClassStatus),
                    refreshing = false,
                )
            }
        }
    }

    fun onWiseduObservation(observation: WiseduAuthObservation) {
        val status = wiseduSource.applyObservation(observation)
        _uiState.update { it.copy(wisedu = it.wisedu.copy(status = status)) }
    }

    fun clearWiseduState() {
        wiseduSource.clear()
        _uiState.update { it.copy(wisedu = it.wisedu.copy(status = AuthStatus.UNKNOWN)) }
    }
}

class AuthCenterViewModelFactory(
    private val wiseduSource: WiseduAuthStatusController,
    private val tronClassSource: AuthStatusSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthCenterViewModel::class.java))
        return AuthCenterViewModel(wiseduSource, tronClassSource) as T
    }

    companion object {
        fun fromApplication(application: Application): AuthCenterViewModelFactory {
            val app = application as? XmuCourseApplication
                ?: error("AuthCenterViewModel requires XmuCourseApplication")
            val context = application.applicationContext
            return AuthCenterViewModelFactory(
                wiseduSource = WiseduAuthStatusSource(SharedPreferencesWiseduSessionMarker(context)),
                tronClassSource = TronClassAuthStatusSource(app.appContainer.tronClass.sessionStore),
            )
        }
    }
}
