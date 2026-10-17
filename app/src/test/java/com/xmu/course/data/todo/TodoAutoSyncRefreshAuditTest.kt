package com.xmu.course.data.todo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoAutoSyncRefreshAuditTest {
    @Test
    fun foregroundAndWidgetDoNotStartDuplicateAutomaticSyncs() = runTest {
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val settings = AuditSettings()
        val coordinator = TodoAutoSyncCoordinator(
            TodoRefreshCoordinator {
                calls++
                finish.await()
                TodoRefreshResult.Success(1)
            },
            settings,
            now = { 100_000L },
        )
        val foreground = async { coordinator.refreshIfDue() }
        runCurrent()
        val widget = async { coordinator.refreshIfDue() }
        runCurrent()
        finish.complete(Unit)
        val results = listOf(foreground.await(), widget.await())

        assertEquals("One successful automatic request must satisfy both callers", 1, calls)
        assertEquals(1, results.count { it is TodoAutoSyncResult.Completed })
        assertEquals(1, results.count { it == TodoAutoSyncResult.SkippedThrottled })
    }

    @Test
    fun successfulTimestampIsRecordedAfterSlowSyncCompletes() = runTest {
        var clock = 1_000L
        val settings = AuditSettings()
        val coordinator = TodoAutoSyncCoordinator(
            TodoRefreshCoordinator {
                clock = 301_000L
                TodoRefreshResult.Success(1)
            },
            settings,
            now = { clock },
        )

        coordinator.refreshIfDue()

        assertEquals("The one-hour interval starts at success, not request start", 301_000L, settings.last)
    }

    @Test
    fun manualRefreshBypassesDisabledAndThrottleButRequiresSession() = runTest {
        var calls = 0
        val settings = object : TodoAutoSyncSettings {
            override fun isEnabled() = false
            override fun setEnabled(enabled: Boolean) = Unit
            override fun lastSuccessfulSyncAt() = 100_000L
            override fun setLastSuccessfulSyncAt(timestamp: Long) = Unit
        }
        var session = true
        val coordinator = TodoAutoSyncCoordinator(
            TodoRefreshCoordinator { calls++; TodoRefreshResult.Success(1) }, settings,
            now = { 100_000L }, isSessionAvailable = { session },
        )
        assertEquals(TodoAutoSyncResult.SkippedDisabled, coordinator.refreshIfDue())
        assertEquals(TodoAutoSyncResult.Completed(TodoRefreshResult.Success(1)), coordinator.refreshNow())
        session = false
        assertEquals(TodoAutoSyncResult.SkippedUnauthenticated, coordinator.refreshNow())
        assertEquals(1, calls)
    }

    private class AuditSettings : TodoAutoSyncSettings {
        var last: Long? = null
        override fun isEnabled() = true
        override fun setEnabled(enabled: Boolean) = Unit
        override fun lastSuccessfulSyncAt() = last
        override fun setLastSuccessfulSyncAt(timestamp: Long) { last = timestamp }
    }
}
