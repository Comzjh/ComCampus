package com.xmu.course.ui.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], application = Application::class)
class CourseReminderQuietChannelTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @Test fun freshChannelIsSilentWithoutVibrationLightsOrDndBypass() {
        CourseReminderNotifications.ensureChannel(context)
        val channel = manager.getNotificationChannel(CourseReminderNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.shouldShowLights())
        assertFalse(channel.canBypassDnd())
        assertTrue(CourseReminderPermissions.notificationsGranted(context))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun legacyAudibleChannelMigratesOnceAndRemainsUnmodified() {
        val legacy = NotificationChannel(CourseReminderNotifications.LEGACY_CHANNEL_ID,
            "课前提醒", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(legacy)
        assertTrue(CourseReminderPermissions.notificationsGranted(context))
        val quiet = manager.getNotificationChannel(CourseReminderNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, quiet.importance)
        assertNull(quiet.sound)
        assertFalse(quiet.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,
            manager.getNotificationChannel(CourseReminderNotifications.LEGACY_CHANNEL_ID).importance)
    }

    @Test fun migrationNeverBypassesPreviouslyBlockedReminderChannel() {
        manager.createNotificationChannel(NotificationChannel(CourseReminderNotifications.LEGACY_CHANNEL_ID,
            "课前提醒", NotificationManager.IMPORTANCE_NONE))
        assertFalse(CourseReminderPermissions.notificationsGranted(context))
        assertEquals(NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(CourseReminderNotifications.CHANNEL_ID).importance)
    }

    @Test fun laterUserSoundAndVibrationChoicesAreNotResetByStartupOrRescheduling() {
        val chosenSound = Uri.parse("content://example/user_selected_tone")
        manager.createNotificationChannel(NotificationChannel(CourseReminderNotifications.CHANNEL_ID,
            "课前提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(chosenSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            enableVibration(true)
        })
        repeat(3) {
            CourseReminderNotifications.ensureChannel(context)
            assertTrue(CourseReminderPermissions.notificationsGranted(context))
        }
        val channel = manager.getNotificationChannel(CourseReminderNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(chosenSound, channel.sound)
        assertTrue(channel.shouldVibrate())
    }

    @Test fun preferencesIntentTargetsOnlyCurrentReminderCategoryWithoutOpeningItAutomatically() {
        val intent = CourseReminderNotifications.settingsIntent(context)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(CourseReminderNotifications.CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertNull(shadowOf(context).nextStartedActivity)
    }
}
