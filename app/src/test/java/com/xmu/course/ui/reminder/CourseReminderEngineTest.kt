package com.xmu.course.ui.reminder

import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReminderEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val target = reminderMillis("2026-10-12T07:30")

    @Test
    fun coldInitializationRescheduleBeforeReceiverPreservesDueAppointment() = runTest {
        val fixture = Fixture()
        fixture.engine().reschedule()
        assertEquals(target, fixture.store.scheduledAt)

        fixture.now = target + 5_000L
        val restarted = fixture.engine()
        restarted.reschedule() // Application.start's initial observation, before onReceive.
        assertEquals(target, fixture.store.scheduledAt)
        assertEquals(target, fixture.alarm.at)
        restarted.onAlarm(target)

        assertEquals(1, fixture.posted.size)
        assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
    }

    @Test
    fun foregroundRescheduleAndReceiverSerializeInBothArrivalOrders() = runTest {
        for (foregroundFirst in listOf(true, false)) {
            val fixture = Fixture()
            fixture.engine().reschedule()
            fixture.now = target + 1_000L
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var reads = 0
            val engine = fixture.engine(state = {
                if (reads++ == 0) {
                    entered.complete(Unit)
                    release.await()
                }
                fixture.state
            })
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                if (foregroundFirst) engine.reschedule() else engine.onAlarm(target)
            }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                if (foregroundFirst) engine.onAlarm(target) else engine.reschedule()
            }
            release.complete(Unit)
            first.await()
            second.await()
            engine.onAlarm(target) // A queued duplicate cannot clear next week's appointment.
            assertEquals("foregroundFirst=$foregroundFirst", 1, fixture.posted.size)
            assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
        }
    }

    @Test
    fun freshInstallationDoesNotCatchUpAnUnscheduledPassedReminder() = runTest {
        val fixture = Fixture()
        fixture.now = target + 1_000L
        val engine = fixture.engine()
        engine.reschedule()
        engine.onAlarm(target)
        assertTrue(fixture.posted.isEmpty())
        assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
    }

    @Test
    fun removedOrSkippedClassInvalidatesPersistedDueAppointment() = runTest {
        for (remove in listOf(true, false)) {
            val fixture = Fixture()
            val engine = fixture.engine()
            engine.reschedule()
            fixture.now = target + 1_000L
            fixture.state = if (remove) fixture.state.copy(courses = emptyList()) else fixture.state.copy(skippedCourseIds = setOf(1L))
            engine.reschedule()
            engine.onAlarm(target)
            assertTrue(fixture.posted.isEmpty())
            assertNull(fixture.store.scheduledAt)
            assertNull(fixture.alarm.at)
        }
    }

    @Test
    fun alreadyStartedClassCannotBeRecoveredAndNextWeekIsStillScheduled() = runTest {
        val fixture = Fixture()
        val engine = fixture.engine()
        engine.reschedule()
        fixture.now = reminderMillis("2026-10-12T08:00")
        engine.onAlarm(target)
        assertTrue(fixture.posted.isEmpty())
        assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
    }

    @Test
    fun disabledPermissionCancelsWithoutReadingOrDeliveringData() = runTest {
        val fixture = Fixture()
        fixture.engine().reschedule()
        fixture.ready = false
        val engine = fixture.engine(state = { error("State must not be read when reminders are unavailable") })
        engine.reschedule()
        assertNull(fixture.alarm.at)
        assertNull(fixture.store.scheduledAt)
        engine.onAlarm(target)
        assertTrue(fixture.posted.isEmpty())
    }

    @Test
    fun dataReadFailureDoesNotEraseAnAppointmentBeforeReceiverCanReadIt() = runTest {
        val fixture = Fixture()
        fixture.engine().reschedule()
        fixture.now = target + 500L
        var failed = false
        try {
            fixture.engine(state = { error("synthetic local read failure") }).reschedule()
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(target, fixture.store.scheduledAt)
        fixture.engine().onAlarm(target)
        assertEquals(1, fixture.posted.size)
    }

    @Test
    fun oneAlarmDeliversEveryClassAtThatTimeAndSchedulesTheFollowingWeek() = runTest {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(courses = fixture.state.courses + fixture.state.courses.single().copy(id = 2L))
        val engine = fixture.engine()
        engine.reschedule()
        fixture.now = target
        engine.onAlarm(target)
        assertEquals(listOf(1L, 2L), fixture.posted.map { it.courseId })
        assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
    }

    @Test
    fun failedNotificationDoesNotReserveTheOccurrenceOrCreateAnImmediateRetryLoop() = runTest {
        val fixture = Fixture()
        val engine = fixture.engine(post = { false })
        engine.reschedule()
        fixture.now = target
        engine.onAlarm(target)
        val occurrence = CourseReminderPlanner.occurrences(fixture.state, zone).first()
        assertFalse(fixture.store.wasDelivered(occurrence.key))
        assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
    }

    @Test
    fun aNewEngineWithTheSameLedgerDoesNotRedeliverTheOccurrence() = runTest {
        val fixture = Fixture()
        val engine = fixture.engine()
        engine.reschedule()
        fixture.now = target
        engine.onAlarm(target)
        val restarted = fixture.engine()
        restarted.reschedule()
        restarted.onAlarm(target)
        assertEquals(1, fixture.posted.size)
        assertEquals(reminderMillis("2026-10-19T07:30"), fixture.store.scheduledAt)
    }

    @Test
    fun earlyBroadcastDoesNotNotifyOrLoseTheOriginalFutureAppointment() = runTest {
        val fixture = Fixture()
        val engine = fixture.engine()
        engine.reschedule()
        engine.onAlarm(target)
        assertTrue(fixture.posted.isEmpty())
        // The guarded receiver received a future appointment; it remains scheduled.
        assertEquals(target, fixture.store.scheduledAt)
    }

    private class Fixture {
        var now = reminderMillis("2026-10-12T07:00")
        var state = reminderState()
        var ready = true
        val alarm = MemoryReminderAlarm()
        val store = MemoryReminderStore()
        val posted = mutableListOf<CourseReminderOccurrence>()

        fun engine(
            state: suspend () -> TimetableFeatureState = { this.state },
            post: (CourseReminderOccurrence) -> Boolean = { posted += it; true },
        ) = CourseReminderEngine(state, { ready }, alarm, store, post, { now }, { ZoneId.of("Asia/Shanghai") })
    }
}

internal class MemoryReminderAlarm : CourseReminderAlarm {
    var at: Long? = null
    override fun cancel() { at = null }
    override fun schedule(atMillis: Long): Boolean { at = atMillis; return true }
}

internal class MemoryReminderStore : CourseReminderDeliveryStore {
    override var scheduledAt: Long? = null
    private val delivered = mutableSetOf<String>()
    override fun wasDelivered(key: String): Boolean = key in delivered
    override fun claim(key: String, nowMillis: Long): Boolean = delivered.add(key)
    override fun release(key: String) { delivered.remove(key) }
}
