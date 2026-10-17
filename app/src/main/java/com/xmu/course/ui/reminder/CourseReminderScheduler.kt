package com.xmu.course.ui.reminder

import android.content.Context
import android.util.Log
import com.xmu.course.XmuCourseApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** No permission dialogs, polling service, or network work. The composition root supplies local state. */
object CourseReminderScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: Job? = null
    private var engine: CourseReminderEngine? = null

    @Synchronized
    fun start(context: Context) {
        if (observer?.isActive == true) return
        val appContext = context.applicationContext
        val repository = (appContext as XmuCourseApplication).appContainer.timetableFeatureRepository
        val settings = CourseReminderSettings(appContext)
        observer = scope.launch {
            merge(
                repository.observeCurrentTimetableState().map { Unit },
                settings.observeEnabled().map { Unit },
            ).conflate().retryWhen { cause, _ ->
                if (cause is CancellationException) false else {
                    Log.w(TAG, "Course reminder observation interrupted; retrying")
                    delay(2_000L)
                    true
                }
            }.collect {
                reschedule(appContext)
            }
        }
    }

    suspend fun reschedule(context: Context) = bounded {
        getEngine(context.applicationContext).reschedule()
    }

    internal suspend fun onAlarm(context: Context, scheduledAtMillis: Long) = bounded {
        getEngine(context.applicationContext).onAlarm(scheduledAtMillis)
    }

    @Synchronized
    private fun getEngine(context: Context): CourseReminderEngine = engine ?: CourseReminderEngine(
        state = {
            (context as XmuCourseApplication).appContainer.timetableFeatureRepository
                .observeCurrentTimetableState().first()
        },
        ready = {
            CourseReminderSettings(context).isEnabled() &&
                CourseReminderPermissions.notificationsGranted(context) &&
                CourseReminderPermissions.exactAlarmsGranted(context)
        },
        alarm = AndroidCourseReminderAlarm(context),
        store = SharedPreferencesCourseReminderDeliveryStore(context),
        post = { CourseReminderNotifications.post(context, it) },
    ).also { engine = it }

    private suspend fun bounded(work: suspend () -> Unit) {
        try {
            withContext(Dispatchers.IO) { withTimeout(7_000L) { work() } }
        } catch (_: TimeoutCancellationException) {
            Log.w(TAG, "Course reminder update timed out")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not log course data or exception messages from the state provider.
            Log.w(TAG, "Course reminder update failed")
        }
    }

    private const val TAG = "CourseReminder"
}
