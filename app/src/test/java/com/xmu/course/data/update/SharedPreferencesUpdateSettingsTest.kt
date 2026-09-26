package com.xmu.course.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesUpdateSettingsTest {

    @Test
    fun wrongPreferenceTypesFallBackWithoutAnAutomaticNetworkRequest() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        try {
            preferences.edit().putString("automatic_check_enabled", "corrupted").commit()
            val settings = SharedPreferencesUpdateSettings(context)
            val api = CountingApi()
            val repository = GitHubUpdateRepository(api, settings, clock = { 100_000L })

            assertEquals(UpdateCheckResult.NotChecked, repository.check("0.8.0-rc1", manual = false))
            assertEquals(0, api.calls)

            preferences.edit()
                .putBoolean("automatic_check_enabled", true)
                .putBoolean("last_automatic_check_at", true)
                .commit()
            val timestamp = settings.lastAutomaticCheckAt()
            assertTrue(timestamp > 0L)
            val throttled = GitHubUpdateRepository(
                api,
                settings,
                clock = { timestamp + 500L },
                throttleMillis = 1_000L,
            )

            assertEquals(UpdateCheckResult.Throttled, throttled.check("0.8.0-rc1", manual = false))
            assertEquals(0, api.calls)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private class CountingApi : GitHubReleaseApiService {
        var calls = 0

        override suspend fun getLatestStableRelease(): Response<GitHubReleaseDto> {
            calls += 1
            return Response.success(
                GitHubReleaseDto(
                    tagName = "v0.8.0",
                    htmlUrl = "https://example.invalid/release",
                    draft = false,
                    prerelease = false,
                ),
            )
        }
    }
}
