package com.xmu.course.ui.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.todo.TodoAutoSyncResult.Completed
import com.xmu.course.data.todo.TodoAutoSyncResult.SkippedDisabled
import com.xmu.course.data.todo.TodoAutoSyncResult.SkippedThrottled
import com.xmu.course.data.todo.TodoAutoSyncResult.SkippedUnauthenticated
import com.xmu.course.data.todo.TodoRefreshResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** WorkManager scheduling for periodic and widget-triggered TronClass todo synchronization. */
object TodoWidgetSyncWork {
    private const val PERIODIC_WORK_NAME = "tronclass_todo_hourly_sync"
    private const val MANUAL_WORK_NAME = "tronclass_todo_widget_manual_sync"
    const val KEY_FORCE_REFRESH = "force_refresh"

    fun scheduleHourly(context: Context) {
        val request = PeriodicWorkRequestBuilder<TodoWidgetSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(KEY_FORCE_REFRESH to false))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueManualRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<TodoWidgetSyncWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(KEY_FORCE_REFRESH to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

/** Performs sync through the app's existing session-aware repository graph. */
@Keep
class TodoWidgetSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? XmuCourseApplication ?: return Result.failure()
            val coordinator = app.appContainer.todo.autoSyncCoordinator
            val forceRefresh = inputData.getBoolean(TodoWidgetSyncWork.KEY_FORCE_REFRESH, false)
            when (val outcome = if (forceRefresh) {
                coordinator.refreshNow()
            } else {
                coordinator.refreshIfDue()
            }) {
                is Completed -> when (outcome.result) {
                    is TodoRefreshResult.Success, TodoRefreshResult.SessionExpired -> Result.success()
                    TodoRefreshResult.Error -> Result.retry()
                }
                SkippedDisabled, SkippedThrottled, SkippedUnauthenticated -> Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            runCatching { WidgetUpdater.updateAll(applicationContext) }
        }
    }
}
