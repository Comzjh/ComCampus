package com.xmu.course.ui.update

import com.xmu.course.data.update.GitHubUpdateRepositoryContract
import com.xmu.course.data.update.UpdateCheckResult
import com.xmu.course.data.update.UpdateSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    @Test
    fun availableUpdateIsDownloadedAutomaticallyAfterStartupCheck() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val apk = File("/cache/ComCampus-0.9.1.apk")
            val repository = FakeRepository(apk)
            val viewModel = UpdateViewModel(repository, FakeSettings(), currentVersion = "0.9.0")

            viewModel.checkAutomatically()
            advanceUntilIdle()

            assertEquals(1, repository.checkCalls)
            assertEquals(1, repository.downloadCalls)
            assertEquals(apk.absolutePath, viewModel.uiState.value.downloadedApkPath)
            assertEquals(true, viewModel.uiState.value.result is UpdateCheckResult.Available)
            assertEquals(true, viewModel.uiState.value.updatePromptPending)

            viewModel.consumeUpdatePrompt()

            assertEquals(false, viewModel.uiState.value.updatePromptPending)
            assertEquals(true, viewModel.uiState.value.result is UpdateCheckResult.Available)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository(private val apk: File) : GitHubUpdateRepositoryContract {
        var checkCalls = 0
        var downloadCalls = 0

        override suspend fun check(currentVersion: String, manual: Boolean): UpdateCheckResult {
            checkCalls += 1
            return UpdateCheckResult.Available(
                currentVersion = currentVersion,
                latestVersion = "v0.9.1",
                releaseUrl = "https://github.com/Comzjh/ComCampus/releases/tag/v0.9.1",
                apkUrl = "https://github.com/Comzjh/ComCampus/releases/download/v0.9.1/ComCampus-0.9.1.apk",
                apkDigest = "sha256:${"a".repeat(64)}",
                apkName = "ComCampus-0.9.1.apk",
            )
        }

        override suspend fun download(update: UpdateCheckResult.Available): File {
            downloadCalls += 1
            return apk
        }
    }

    private class FakeSettings : UpdateSettings {
        private var enabled = true
        private var lastCheckAt = 0L

        override fun isAutomaticCheckEnabled() = enabled
        override fun setAutomaticCheckEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun lastAutomaticCheckAt() = lastCheckAt
        override fun setLastAutomaticCheckAt(timestamp: Long) { lastCheckAt = timestamp }
    }
}
