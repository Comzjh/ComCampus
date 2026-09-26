package com.xmu.course.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xmu.course.XmuCourseApplication
import com.xmu.course.BuildConfig
import com.xmu.course.data.update.GitHubUpdateRepositoryContract
import com.xmu.course.data.update.UpdateCheckResult
import com.xmu.course.data.update.UpdateSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val automaticCheckEnabled: Boolean = true,
    val checking: Boolean = false,
    val result: UpdateCheckResult = UpdateCheckResult.NotChecked,
    val downloading: Boolean = false,
    val downloadedApkPath: String? = null,
    val downloadError: Boolean = false,
)

class UpdateViewModel(
    private val repository: GitHubUpdateRepositoryContract,
    private val settings: UpdateSettings,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateUiState(automaticCheckEnabled = settings.isAutomaticCheckEnabled()))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkAutomatically() {
        check(manual = false)
    }

    fun checkManually() {
        check(manual = true)
    }

    fun setAutomaticCheckEnabled(enabled: Boolean) {
        settings.setAutomaticCheckEnabled(enabled)
        _uiState.update { it.copy(automaticCheckEnabled = enabled) }
    }

    fun dismissResult() {
        _uiState.update { it.copy(result = UpdateCheckResult.NotChecked) }
    }

    fun download(update: UpdateCheckResult.Available) {
        if (_uiState.value.downloading || update.apkUrl == null || update.apkDigest == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloading = true, downloadedApkPath = null, downloadError = false) }
            try {
                val apk = repository.download(update)
                _uiState.update { it.copy(downloading = false, downloadedApkPath = apk.absolutePath) }
            } catch (_: Exception) {
                _uiState.update { it.copy(downloading = false, downloadError = true) }
            }
        }
    }

    private fun check(manual: Boolean) {
        if (_uiState.value.checking) return
        viewModelScope.launch {
            _uiState.update { it.copy(checking = true, result = UpdateCheckResult.NotChecked) }
            val result = repository.check(currentVersion, manual)
            _uiState.update { it.copy(checking = false, result = result) }
            if (result is UpdateCheckResult.Available && result.apkUrl != null && result.apkDigest != null) {
                download(result)
            }
        }
    }
}

class UpdateViewModelFactory(
    private val repository: GitHubUpdateRepositoryContract,
    private val settings: UpdateSettings,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(UpdateViewModel::class.java))
        return UpdateViewModel(repository, settings) as T
    }

    companion object {
        fun fromApplication(application: android.app.Application): UpdateViewModelFactory {
            val app = application as? XmuCourseApplication
                ?: error("UpdateViewModel requires XmuCourseApplication")
            val update = app.appContainer.update
            return UpdateViewModelFactory(
                update.repository,
                update.settings,
            )
        }
    }
}
