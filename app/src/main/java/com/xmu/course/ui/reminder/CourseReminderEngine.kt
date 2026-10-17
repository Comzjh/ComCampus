package com.xmu.course.ui.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.ui.timetable.TimeTableConfig
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface CourseReminderAlarm {
    fun cancel()
    fun schedule(atMillis: Long): Boolean
}

internal interface CourseReminderDeliveryStore {
    var scheduledAt: Long?
    fun wasDelivered(key: String): Boolean
    fun claim(key: String, nowMillis: Long): Boolean
    fun release(key: String)
}

/** All scheduling and delivery paths share one mutex, including foreground permission returns. */
internal class CourseReminderEngine(
    private val state: suspend () -> TimetableFeatureState,
    private val ready: () -> Boolean,
    private val alarm: CourseReminderAlarm,
    private val store: CourseReminderDeliveryStore,
    private val post: (CourseReminderOccurrence) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val sectionTimes: () -> List<String> = { TimeTableConfig.xmuSections },
) {
    private val mutex = Mutex()

    suspend fun reschedule() = mutex.withLock {
        scheduleNext(recoverPending = true)
    }

    suspend fun onAlarm(expectedAtMillis: Long) = mutex.withLock {
        // A canceled/replaced alarm or duplicate broadcast must not disturb the next valid alarm.
        if (expectedAtMillis <= 0 || store.scheduledAt != expectedAtMillis) return@withLock
        if (!ready()) {
            cancel()
            return@withLock
        }
        if (expectedAtMillis > now()) {
            scheduleNext()
            return@withLock
        }
        // Keep the persisted appointment intact until local state is actually available. On a
        // cold alarm launch the Application observer and this receiver can arrive in either order.
        val current = CourseReminderPlanner.occurrences(state(), zoneId(), sectionTimes())
        cancel()
        try {
            for (occurrence in CourseReminderPlanner.due(current, expectedAtMillis, now())) {
                if (!ready() || occurrence.startAtMillis <= now()) break
                // Persist before delivery: a restarted process cannot alert the same occurrence again.
                if (!store.claim(occurrence.key, now())) continue
                var delivered = false
                try {
                    delivered = post(occurrence)
                } finally {
                    if (!delivered) store.release(occurrence.key)
                }
            }
        } finally {
            // Read again: edits arriving during notification delivery also affect the following alarm.
            scheduleNext(afterMillis = expectedAtMillis)
        }
    }

    private fun cancel() {
        alarm.cancel()
        store.scheduledAt = null
    }

    private suspend fun scheduleNext(afterMillis: Long = Long.MIN_VALUE, recoverPending: Boolean = false) {
        if (!ready()) {
            cancel()
            return
        }
        val current = CourseReminderPlanner.occurrences(state(), zoneId(), sectionTimes())
        val nowMillis = now()
        val earliest = maxOf(nowMillis, if (afterMillis == Long.MAX_VALUE) afterMillis else afterMillis + 1)
        // Do not catch up historical classes: only a persisted, due, still-valid appointment may
        // survive foreground/cold-start rescheduling. Re-arming its past target fires it promptly.
        val pending = store.scheduledAt?.takeIf { scheduled ->
            recoverPending && CourseReminderPlanner.due(current, scheduled, nowMillis).any { !store.wasDelivered(it.key) }
        }
        val next = pending ?: CourseReminderPlanner.next(current, earliest, store::wasDelivered)
        cancel()
        if (next == null || !ready()) return
        if (alarm.schedule(next)) store.scheduledAt = next
    }
}

internal class AndroidCourseReminderAlarm(context: Context) : CourseReminderAlarm {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(AlarmManager::class.java)

    override fun cancel() {
        val pending = pendingIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        manager?.cancel(pending)
        pending.cancel()
    }

    @SuppressLint("ScheduleExactAlarm") // Rechecked here as well as in the scheduling engine.
    override fun schedule(atMillis: Long): Boolean {
        if (!CourseReminderPermissions.exactAlarmsGranted(context)) return false
        val pending = pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT, atMillis) ?: return false
        return try {
            val alarmManager = manager ?: return false
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            true
        } catch (_: SecurityException) {
            cancel()
            false
        }
    }

    private fun pendingIntent(flags: Int, atMillis: Long = 0L): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CourseReminderReceiver::class.java).apply {
            action = CourseReminderReceiver.ACTION_REMIND
            putExtra(CourseReminderReceiver.EXTRA_SCHEDULED_AT, atMillis)
        },
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 30030
    }
}

internal class SharedPreferencesCourseReminderDeliveryStore(context: Context) : CourseReminderDeliveryStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        CourseReminderSettings.PREFERENCES_NAME, Context.MODE_PRIVATE,
    )

    override var scheduledAt: Long?
        get() = try {
            preferences.getLong(KEY_SCHEDULED_AT, 0L).takeIf { it > 0 }
        } catch (_: ClassCastException) {
            null
        }
        set(value) {
            // Receiver work runs on IO; commit survives immediate process termination.
            preferences.edit().apply {
                if (value == null) remove(KEY_SCHEDULED_AT) else putLong(KEY_SCHEDULED_AT, value)
            }.commit()
        }

    override fun wasDelivered(key: String): Boolean = key in delivered()

    override fun claim(key: String, nowMillis: Long): Boolean {
        val keys = delivered()
        if (key in keys) return false
        val retained = keys.filter { (it.substringAfterLast(':').toLongOrNull() ?: 0L) >= nowMillis - RETENTION_MILLIS }
            .toMutableSet().apply { add(key) }
        return preferences.edit().putStringSet(KEY_DELIVERED, retained).commit()
    }

    override fun release(key: String) {
        preferences.edit().putStringSet(KEY_DELIVERED, delivered() - key).commit()
    }

    private fun delivered(): Set<String> = try {
        preferences.getStringSet(KEY_DELIVERED, emptySet()).orEmpty().toSet()
    } catch (_: ClassCastException) {
        emptySet()
    }

    private companion object {
        const val KEY_SCHEDULED_AT = "scheduled_at"
        const val KEY_DELIVERED = "delivered_occurrences"
        const val RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1_000L
    }
}
