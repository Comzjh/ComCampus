package com.xmu.course.data.todo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesTodoAutoSyncSettingsTest {
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences("todo_auto_sync", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun wrongEnabledTypeDefaultsClosedAndLeavesStoredValueIntact() {
        preferences.edit().putString("enabled", "invalid").commit()

        val settings = SharedPreferencesTodoAutoSyncSettings(ApplicationProvider.getApplicationContext())

        assertFalse(settings.isEnabled())
        assertEquals("invalid", preferences.all["enabled"])
    }

    @Test
    fun wrongLastSyncTypeThrottlesWithoutCallingRefresh() = kotlinx.coroutines.test.runTest {
        preferences.edit()
            .putBoolean("enabled", true)
            .putString("last_successful_sync_at", "invalid")
            .commit()
        val settings = SharedPreferencesTodoAutoSyncSettings(ApplicationProvider.getApplicationContext())
        val last = settings.lastSuccessfulSyncAt()
        assertNotNull(last)

        val refresh = CountingRefreshCoordinator()
        val coordinator = TodoAutoSyncCoordinator(
            refreshCoordinator = refresh,
            settings = settings,
            now = { last!! },
            intervalMillis = 60_000L,
        )

        assertEquals(TodoAutoSyncResult.SkippedThrottled, coordinator.refreshIfDue())
        assertEquals(0, refresh.calls)
        assertEquals("invalid", preferences.all["last_successful_sync_at"])
    }

    private class CountingRefreshCoordinator : TodoRefreshCoordinator {
        var calls = 0
            private set

        override suspend fun refresh(): TodoRefreshResult {
            calls += 1
            return TodoRefreshResult.Success(importedCount = 0)
        }
    }
}
