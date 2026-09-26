package com.xmu.course.data.todo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoAutoSyncTest {
    @Test
    fun `successful foreground sync records time`() = runTest {
        val settings = FakeSettings(enabled = true, last = null)
        val refresh = FakeRefresh(TodoRefreshResult.Success(4))
        val coordinator = TodoAutoSyncCoordinator(refresh, settings, now = { 10_000L }, intervalMillis = 3_600L)

        assertEquals(
            TodoAutoSyncResult.Completed(TodoRefreshResult.Success(4)),
            coordinator.refreshIfDue(),
        )
        assertEquals(10_000L, settings.last)
        assertEquals(1, refresh.calls)
    }

    @Test
    fun `recent success throttles another foreground sync`() = runTest {
        val settings = FakeSettings(enabled = true, last = 8_000L)
        val refresh = FakeRefresh(TodoRefreshResult.Success(4))
        val coordinator = TodoAutoSyncCoordinator(refresh, settings, now = { 10_000L }, intervalMillis = 3_600L)

        assertEquals(TodoAutoSyncResult.SkippedThrottled, coordinator.refreshIfDue())
        assertEquals(0, refresh.calls)
        assertEquals(8_000L, settings.last)
    }

    @Test
    fun `disabled setting never calls network coordinator`() = runTest {
        val settings = FakeSettings(enabled = false, last = null)
        val refresh = FakeRefresh(TodoRefreshResult.Success(4))
        val coordinator = TodoAutoSyncCoordinator(refresh, settings, now = { 10_000L })

        assertEquals(TodoAutoSyncResult.SkippedDisabled, coordinator.refreshIfDue())
        assertEquals(0, refresh.calls)
        assertNull(settings.last)
    }

    @Test
    fun `missing session silently skips foreground sync`() = runTest {
        val settings = FakeSettings(enabled = true, last = null)
        val refresh = FakeRefresh(TodoRefreshResult.Success(4))
        val coordinator = TodoAutoSyncCoordinator(
            refresh,
            settings,
            now = { 10_000L },
            isSessionAvailable = { false },
        )

        assertEquals(TodoAutoSyncResult.SkippedUnauthenticated, coordinator.refreshIfDue())
        assertEquals(0, refresh.calls)
        assertNull(settings.last)
    }

    @Test
    fun `failed sync does not advance successful timestamp`() = runTest {
        val settings = FakeSettings(enabled = true, last = 1_000L)
        val refresh = FakeRefresh(TodoRefreshResult.Error)
        val coordinator = TodoAutoSyncCoordinator(refresh, settings, now = { 10_000L }, intervalMillis = 3_600L)

        val result = coordinator.refreshIfDue()

        assertTrue(result is TodoAutoSyncResult.Completed)
        assertEquals(1_000L, settings.last)
    }

    private class FakeRefresh(private val result: TodoRefreshResult) : TodoRefreshCoordinator {
        var calls = 0
        override suspend fun refresh(): TodoRefreshResult {
            calls += 1
            return result
        }
    }

    private class FakeSettings(
        private var enabled: Boolean,
        var last: Long?,
    ) : TodoAutoSyncSettings {
        override fun isEnabled(): Boolean = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun lastSuccessfulSyncAt(): Long? = last
        override fun setLastSuccessfulSyncAt(timestamp: Long) { last = timestamp }
    }
}
