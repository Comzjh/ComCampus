package com.xmu.course.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.widget.WidgetDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Widget 统一刷新入口。
 *
 * - App 启动 / 数据库变化 / 课表切换：启动 observer
 * - 下一节课倒计时：由系统 Alarm 每分钟低功耗触发
 */
object WidgetUpdater {

    private const val ACTION_MINUTE_TICK = "com.xmu.course.widget.MINUTE_TICK"
    private const val REQUEST_CODE_MINUTE_TICK = 1001

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Application 启动时调用，保持 Widget 跟随当前课表和 Room 数据。 */
    fun start(
        context: Context,
        dataSource: WidgetDataSource = WidgetDataSource.from(context),
    ) {
        val appContext = context.applicationContext
        TimetablePrefs.load(appContext)
        // 应用启动或桌面首次添加组件时，先登记一次分钟刷新；后续由 receiver 续期。
        scheduleMinuteRefresh(appContext)

        appScope.launch {
            dataSource.observeInvalidation().collectLatest {
                updateAll(appContext)
            }
        }
    }

    /** Re-render widgets after system configuration changes such as light/dark mode. */
    fun refreshForConfigurationChange(context: Context) {
        val appContext = context.applicationContext
        appScope.launch { updateAll(appContext) }
    }

    /** Refresh every widget backed by the shared local database. */
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)
        manager.getGlanceIds(TodayCourseWidget::class.java).forEach { glanceId ->
            TodayCourseWidget().update(appContext, glanceId)
        }
        manager.getGlanceIds(NextCourseWidget::class.java).forEach { glanceId ->
            NextCourseWidget().update(appContext, glanceId)
        }
        manager.getGlanceIds(TodoWidget::class.java).forEach { glanceId ->
            TodoWidget().update(appContext, glanceId)
        }
    }

    /** 启动每分钟倒计时刷新；非 exact alarm，系统允许小延迟。 */
    fun scheduleMinuteRefresh(context: Context) {
        val appContext = context.applicationContext
        val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, NextCourseWidgetReceiver::class.java).apply {
            action = ACTION_MINUTE_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_MINUTE_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 60_000L,
            pendingIntent,
        )
    }
}
