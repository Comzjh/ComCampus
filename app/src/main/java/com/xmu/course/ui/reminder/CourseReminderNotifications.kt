package com.xmu.course.ui.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.xmu.course.MainActivity
import com.xmu.course.R

/** Uses the existing activity extra without importing the Widget implementation. */
fun buildCourseReminderIntent(context: Context, occurrence: CourseReminderOccurrence): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        data = Uri.parse("comcampus://course-reminder/${occurrence.timetableId}/${occurrence.courseId}/${occurrence.startAtMillis}")
        putExtra("extra_widget_course_id", occurrence.courseId)
    }

object CourseReminderNotifications {
    const val CHANNEL_ID = "course_reminders_quiet_v2"
    internal const val LEGACY_CHANNEL_ID = "course_reminders"
    internal const val NOTIFICATION_ID = 30

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Channel sound/importance cannot be reliably changed after creation. Migrate once
        // to a quiet channel, then leave subsequent user choices completely untouched.
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val legacyBlocked = manager.getNotificationChannel(LEGACY_CHANNEL_ID)
            ?.importance == NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "课前提醒", if (legacyBlocked) {
                NotificationManager.IMPORTANCE_NONE
            } else {
                NotificationManager.IMPORTANCE_LOW
            }).apply {
                description = "课前30分钟显示课程、时间和地点；默认静默，声音和震动可自行开启"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }

    internal fun build(
        context: Context,
        occurrence: CourseReminderOccurrence,
        nowMillis: Long = System.currentTimeMillis(),
    ): Notification {
        val text = buildString {
            append(occurrence.startTime).append(" 开始")
            if (occurrence.location.isNotBlank()) append(" · ").append(occurrence.location)
        }
        val content = PendingIntent.getActivity(
            context, 0, buildCourseReminderIntent(context, occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_course_reminder)
            .setContentTitle("课前提醒")
            .setContentText("即将上课，解锁查看详情")
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_course_reminder)
            .setContentTitle(occurrence.courseName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setWhen(occurrence.startAtMillis)
            .setShowWhen(false)
            .setTimeoutAfter((occurrence.startAtMillis - nowMillis).coerceAtLeast(1L))
            .build()
    }

    internal fun post(
        context: Context,
        occurrence: CourseReminderOccurrence,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        ensureChannel(context)
        if (!CourseReminderSettings(context).isEnabled() || !CourseReminderPermissions.notificationsGranted(context) ||
            occurrence.startAtMillis <= nowMillis
        ) {
            return false
        }
        return try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(occurrence.key, NOTIFICATION_ID, build(context, occurrence, nowMillis)) ?: return false
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
