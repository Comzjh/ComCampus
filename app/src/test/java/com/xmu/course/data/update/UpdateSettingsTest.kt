package com.xmu.course.data.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class UpdateSettingsTest {
    @Test
    fun `automatic check defaults on and can persist off`() {
        val settings = FakeSettings()
        assertTrue(settings.isAutomaticCheckEnabled())
        settings.setAutomaticCheckEnabled(false)
        assertFalse(settings.isAutomaticCheckEnabled())
    }

    @Test
    fun `automatic checks are throttled but manual checks ignore throttle`() = runTest {
        val settings = FakeSettings().apply { last = 90_000L }
        val api = FakeApi(Response.success(release("v1.2.4")))
        val repository = GitHubUpdateRepository(api, settings, clock = { 100_000L }, throttleMillis = 24 * 60 * 60 * 1000L)

        assertEquals(UpdateCheckResult.Throttled, repository.check("1.2.3", manual = false))
        assertEquals(UpdateCheckResult.Available("1.2.3", "v1.2.4", "https://github.com/Comzjh/ComCampus/releases/tag/v1.2.4"), repository.check("1.2.3", manual = true))
        assertEquals(1, api.calls)
    }

    @Test
    fun `automatic disabled still permits manual check`() = runTest {
        val settings = FakeSettings().apply { enabled = false }
        val api = FakeApi(Response.success(release("v1.2.4")))
        val repository = GitHubUpdateRepository(api, settings, clock = { 100_000L })

        assertEquals(UpdateCheckResult.NotChecked, repository.check("1.2.3", manual = false))
        assertEquals(UpdateCheckResult.Available("1.2.3", "v1.2.4", "https://github.com/Comzjh/ComCampus/releases/tag/v1.2.4"), repository.check("1.2.3", manual = true))
    }

    @Test
    fun `update result carries only a trusted GitHub APK asset and digest`() = runTest {
        val release = release("v1.2.4").copy(
            assets = listOf(
                GitHubReleaseAssetDto(
                    name = "ComCampus-1.2.4.apk",
                    browserDownloadUrl = "https://github.com/Comzjh/ComCampus/releases/download/v1.2.4/ComCampus-1.2.4.apk",
                    digest = "sha256:${"a".repeat(64)}",
                ),
                GitHubReleaseAssetDto(
                    name = "unsafe.apk",
                    browserDownloadUrl = "https://attacker.example/ComCampus.apk",
                    digest = "sha256:${"b".repeat(64)}",
                ),
            ),
        )
        val repository = GitHubUpdateRepository(FakeApi(Response.success(release)), FakeSettings())

        assertEquals(
            UpdateCheckResult.Available(
                currentVersion = "1.2.3",
                latestVersion = "v1.2.4",
                releaseUrl = "https://github.com/Comzjh/ComCampus/releases/tag/v1.2.4",
                apkUrl = "https://github.com/Comzjh/ComCampus/releases/download/v1.2.4/ComCampus-1.2.4.apk",
                apkDigest = "sha256:${"a".repeat(64)}",
                apkName = "ComCampus-1.2.4.apk",
            ),
            repository.check("1.2.3", manual = true),
        )
    }

    private fun release(tag: String) = GitHubReleaseDto(
        tagName = tag,
        htmlUrl = "https://github.com/Comzjh/ComCampus/releases/tag/$tag",
        draft = false,
        prerelease = false,
    )

    private class FakeSettings : UpdateSettings {
        var enabled = true
        var last = 0L
        override fun isAutomaticCheckEnabled() = enabled
        override fun setAutomaticCheckEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun lastAutomaticCheckAt() = last
        override fun setLastAutomaticCheckAt(timestamp: Long) { last = timestamp }
    }

    private class FakeApi(private val response: Response<GitHubReleaseDto>) : GitHubReleaseApiService {
        var calls = 0
        override suspend fun getLatestStableRelease(): Response<GitHubReleaseDto> {
            calls += 1
            return response
        }
    }
}
