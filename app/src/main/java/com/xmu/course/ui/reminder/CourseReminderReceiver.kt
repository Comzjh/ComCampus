package com.xmu.course.ui.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Explicit, non-exported alarm target. Manifest registration is performed by the app integration. */
class CourseReminderReceiver internal constructor(
    private val handle: suspend (Context, Long) -> Unit,
    private val scope: CoroutineScope,
) : BroadcastReceiver() {
    constructor() : this(CourseReminderScheduler::onAlarm, CourseReminderReceiverWork.scope)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND || intent.component != ComponentName(context, CourseReminderReceiver::class.java)) return
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        if (scheduledAt <= 0L) return
        CourseReminderReceiverWork.launch(this, scope) { handle(context.applicationContext, scheduledAt) }
    }

    companion object {
        const val ACTION_REMIND = "com.comcampus.app.action.COURSE_REMINDER"
        const val EXTRA_SCHEDULED_AT = "course_reminder_scheduled_at"
    }
}

/** Receives only the system events declared by the integration, then checks permissions again. */
class CourseReminderRescheduleReceiver internal constructor(
    private val reschedule: suspend (Context) -> Unit,
    private val scope: CoroutineScope,
) : BroadcastReceiver() {
    constructor() : this(CourseReminderScheduler::reschedule, CourseReminderReceiverWork.scope)

    override fun onReceive(context: Context, intent: Intent) {
        if (!handlesAction(intent.action)) return
        CourseReminderReceiverWork.launch(this, scope) { reschedule(context.applicationContext) }
    }

    companion object {
        fun handlesAction(action: String?): Boolean = action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

private object CourseReminderReceiverWork {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(receiver: BroadcastReceiver, scope: CoroutineScope, work: suspend () -> Unit) {
        val pending = receiver.goAsync()
        scope.launch {
            try {
                withTimeout(8_000L) { work() }
            } catch (_: TimeoutCancellationException) {
                Log.w("CourseReminder", "Course reminder receiver timed out")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w("CourseReminder", "Course reminder receiver failed")
            } finally {
                pending?.finish()
            }
        }
    }
}
