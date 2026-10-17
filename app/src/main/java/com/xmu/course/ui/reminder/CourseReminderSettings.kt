package com.xmu.course.ui.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Local reminder preference. Permission requests belong exclusively to the settings UI. */
class CourseReminderSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = try {
        preferences.getBoolean(KEY_ENABLED, true)
    } catch (_: ClassCastException) {
        true
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    internal fun observeEnabled(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_ENABLED) trySend(isEnabled())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(isEnabled())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        const val PREFERENCES_NAME = "course_reminders"
        const val KEY_ENABLED = "enabled"
    }
}

object CourseReminderPermissions {
    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.getNotificationChannel(CourseReminderNotifications.CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun exactAlarmsGranted(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || try {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    } catch (_: SecurityException) {
        false
    }

    fun needsAttention(context: Context): Boolean = CourseReminderSettings(context).isEnabled() &&
        (!notificationsGranted(context) || !exactAlarmsGranted(context))
}
