package com.xmu.course.ui.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Android services use Robolectric; course records and wall-clock values are synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CourseReminderAndroidTest {
    private lateinit var context: Application
    private lateinit var alarms: AlarmManager
    private lateinit var notifications: NotificationManager
    private lateinit var receiverScope: CoroutineScope

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(CourseReminderSettings.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        alarms = context.getSystemService(AlarmManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notifications).setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ShadowAlarmManager.setAutoSchedule(false)
        receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun cleanup() {
        receiverScope.cancel()
        AndroidCourseReminderAlarm(context).cancel()
        notifications.cancelAll()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    @Test
    fun enabledDefaultsToTrueAndPersistsWithoutLaunchingAPermissionRequest() = runBlocking {
        assertTrue(CourseReminderSettings(context).isEnabled())
        CourseReminderSettings(context).setEnabled(false)
        assertFalse(CourseReminderSettings(context).isEnabled())
        assertFalse(CourseReminderSettings(context).observeEnabled().first())
        CourseReminderSettings(context).setEnabled(true)
        assertTrue(CourseReminderSettings(context).observeEnabled().first())
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun needsAttentionRequiresEnabledAndBothPermissionsWithoutStartingAnyActivity() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(CourseReminderPermissions.notificationsGranted(context))
        assertFalse(CourseReminderPermissions.exactAlarmsGranted(context))
        assertTrue(CourseReminderPermissions.needsAttention(context))
        CourseReminderSettings(context).setEnabled(false)
        assertFalse(CourseReminderPermissions.needsAttention(context))
        CourseReminderSettings(context).setEnabled(true)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(CourseReminderPermissions.needsAttention(context))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertFalse(CourseReminderPermissions.needsAttention(context))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun disabledApplicationNotificationsAndBlockedReminderChannelAreBothDetected() {
        shadowOf(notifications).setNotificationsEnabled(false)
        assertFalse(CourseReminderPermissions.notificationsGranted(context))
        shadowOf(notifications).setNotificationsEnabled(true)
        notifications.createNotificationChannel(NotificationChannel(
            CourseReminderNotifications.CHANNEL_ID, "课前提醒", NotificationManager.IMPORTANCE_NONE,
        ))
        assertFalse(CourseReminderPermissions.notificationsGranted(context))
        assertTrue(CourseReminderPermissions.needsAttention(context))
    }

    @Test
    @Config(sdk = [26])
    fun androidEightDoesNotRequirePostNotificationsOrExactAlarmSpecialAccess() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(CourseReminderPermissions.exactAlarmsGranted(context))
        assertTrue(CourseReminderPermissions.notificationsGranted(context))
        assertFalse(CourseReminderPermissions.needsAttention(context))
    }

    @Test
    fun alarmUsesOneExplicitImmutableExactWakeupAndReplacesThePreviousAppointment() {
        val alarm = AndroidCourseReminderAlarm(context)
        val first = reminderMillis("2026-10-12T07:30")
        assertTrue(alarm.schedule(first))
        assertTrue(alarm.schedule(first + 1_000L))
        val appointment = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, appointment.type)
        assertEquals(first + 1_000L, appointment.triggerAtMs)
        assertEquals(0L, appointment.windowLengthMs)
        assertTrue(appointment.isAllowWhileIdle)
        val pending = shadowOf(appointment.operation)
        assertTrue(pending.isImmutable)
        assertTrue(pending.isBroadcast)
        assertEquals(ComponentName(context, CourseReminderReceiver::class.java), pending.savedIntent.component)
        assertEquals(CourseReminderReceiver.ACTION_REMIND, pending.savedIntent.action)
        assertEquals(first + 1_000L, pending.savedIntent.getLongExtra(CourseReminderReceiver.EXTRA_SCHEDULED_AT, 0L))
        alarm.cancel()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    fun missingExactAlarmPermissionNeverSchedulesAnInexactReplacement() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(AndroidCourseReminderAlarm(context).schedule(reminderMillis("2026-10-12T07:30")))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun notificationShowsNameTimeAndOnlyNonblankLocationWithPrivateLockscreenContent() {
        val occurrence = CourseReminderPlanner.occurrences(reminderState(), ZoneId.of("Asia/Shanghai")).first()
        CourseReminderNotifications.ensureChannel(context)
        val notification = CourseReminderNotifications.build(context, occurrence, occurrence.remindAtMillis)
        assertEquals(CourseReminderNotifications.CHANNEL_ID, notification.channelId)
        assertEquals("测试课程", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("08:00 开始 · 教学楼 101", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNull(notification.fullScreenIntent)
        assertEquals(CourseReminderPlanner.LEAD_TIME_MILLIS, notification.timeoutAfter)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val publicText = notification.publicVersion.extras.toString()
        assertFalse(publicText.contains("测试课程"))
        assertFalse(publicText.contains("教学楼"))
        val noLocation = CourseReminderNotifications.build(context, occurrence.copy(location = "  "), occurrence.remindAtMillis)
        assertEquals("08:00 开始", noLocation.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val pending = shadowOf(notification.contentIntent)
        assertTrue(pending.isImmutable)
        assertEquals(1L, pending.savedIntent.getLongExtra("extra_widget_course_id", 0L))
        assertEquals("com.xmu.course.MainActivity", pending.savedIntent.component!!.className)
    }

    @Test
    fun simultaneousNotificationsHaveIndependentTagsAndClickTargets() {
        val occurrence = CourseReminderPlanner.occurrences(reminderState(), ZoneId.of("Asia/Shanghai")).first()
        val another = occurrence.copy(courseId = 2L, courseName = "第二门课")
        assertTrue(CourseReminderNotifications.post(context, occurrence, occurrence.remindAtMillis))
        assertTrue(CourseReminderNotifications.post(context, another, another.remindAtMillis))
        assertEquals(2, shadowOf(notifications).size())
        val first = shadowOf(notifications).getNotification(occurrence.key, CourseReminderNotifications.NOTIFICATION_ID)
        val second = shadowOf(notifications).getNotification(another.key, CourseReminderNotifications.NOTIFICATION_ID)
        assertNotEquals(first.contentIntent, second.contentIntent)
        assertNotEquals(shadowOf(first.contentIntent).savedIntent.data, shadowOf(second.contentIntent).savedIntent.data)
    }

    @Test
    fun notificationIsNotSentAfterClassStartsOrWhenUserDisabledIt() {
        val occurrence = CourseReminderPlanner.occurrences(reminderState(), ZoneId.of("Asia/Shanghai")).first()
        assertFalse(CourseReminderNotifications.post(context, occurrence, occurrence.startAtMillis))
        CourseReminderSettings(context).setEnabled(false)
        assertFalse(CourseReminderNotifications.post(context, occurrence, occurrence.remindAtMillis))
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test
    fun persistedLedgerSurvivesNewInstancesAndExpiredEntriesArePruned() {
        val occurrence = CourseReminderPlanner.occurrences(reminderState(), ZoneId.of("Asia/Shanghai")).first()
        val first = SharedPreferencesCourseReminderDeliveryStore(context)
        first.scheduledAt = occurrence.remindAtMillis
        assertTrue(first.claim(occurrence.key, occurrence.remindAtMillis))
        val restarted = SharedPreferencesCourseReminderDeliveryStore(context)
        assertEquals(occurrence.remindAtMillis, restarted.scheduledAt)
        assertTrue(restarted.wasDelivered(occurrence.key))
        assertFalse(restarted.claim(occurrence.key, occurrence.remindAtMillis))
        val later = occurrence.copy(startAtMillis = occurrence.startAtMillis + 14 * 24 * 60 * 60 * 1_000L)
        assertTrue(restarted.claim(later.key, later.startAtMillis))
        assertFalse(restarted.wasDelivered(occurrence.key))
        restarted.release(later.key)
        assertFalse(restarted.wasDelivered(later.key))
    }

    @Test
    fun receiveRechecksDeletionSkippingAndTimetableSwitchInsteadOfUsingAlarmExtrasAsCourseData() = runBlocking {
        for (change in listOf("deleted", "skipped", "switched", "moved")) {
            var state = reminderState()
            var now = reminderMillis("2026-10-12T07:00")
            val store = SharedPreferencesCourseReminderDeliveryStore(context)
            val engine = androidEngine({ state }, store, { now })
            engine.reschedule()
            val intent = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
            now = reminderMillis("2026-10-12T07:30")
            state = when (change) {
                "deleted" -> state.copy(courses = emptyList())
                "skipped" -> state.copy(skippedCourseIds = setOf(1L))
                "switched" -> state.copy(
                    timetable = state.timetable!!.copy(id = 2L, semesterId = 20L),
                    courses = listOf(state.courses.single().copy(id = 2L, semesterId = 20L, startSection = 3)),
                )
                else -> state.copy(courses = listOf(state.courses.single().copy(startSection = 3)))
            }
            CourseReminderReceiver({ _, at -> engine.onAlarm(at) }, receiverScope).onReceive(context, intent)
            assertEquals("change=$change", 0, shadowOf(notifications).size())
            if (change == "switched" || change == "moved") {
                assertEquals(reminderMillis("2026-10-12T09:40"), store.scheduledAt)
            } else assertNull(store.scheduledAt)
            AndroidCourseReminderAlarm(context).cancel()
        }
    }

    @Test
    fun coldStartRescheduleThenReceivePostsOnceAndRetainsTheFollowingAlarm() = runBlocking {
        var now = reminderMillis("2026-10-12T07:00")
        androidEngine({ reminderState() }, SharedPreferencesCourseReminderDeliveryStore(context), { now }).reschedule()
        val original = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        now = reminderMillis("2026-10-12T07:30") + 5_000L
        val store = SharedPreferencesCourseReminderDeliveryStore(context)
        val restarted = androidEngine({ reminderState() }, store, { now })
        restarted.reschedule()
        assertEquals(reminderMillis("2026-10-12T07:30"), store.scheduledAt)
        val receiver = CourseReminderReceiver({ _, at -> restarted.onAlarm(at) }, receiverScope)
        receiver.onReceive(context, original)
        receiver.onReceive(context, original)
        assertEquals(1, shadowOf(notifications).size())
        assertEquals(reminderMillis("2026-10-19T07:30"), store.scheduledAt)
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test(timeout = 10_000L)
    fun permissionReturnAndReceiverConcurrentArrivalProduceOneAndroidNotification() = runBlocking {
        for (foregroundFirst in listOf(true, false)) {
            var now = reminderMillis("2026-10-12T07:00")
            context.getSharedPreferences(CourseReminderSettings.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            val store = SharedPreferencesCourseReminderDeliveryStore(context)
            androidEngine({ reminderState() }, store, { now }).reschedule()
            val intent = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
            val target = store.scheduledAt!!
            now = target + 1_000L
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val handled = CompletableDeferred<Unit>()
            var reads = 0
            val engine = androidEngine({
                if (reads++ == 0) {
                    entered.complete(Unit)
                    release.await()
                }
                reminderState()
            }, store, { now })
            val receiver = CourseReminderReceiver({ _, at ->
                try {
                    engine.onAlarm(at)
                } finally {
                    handled.complete(Unit)
                }
            }, receiverScope)
            val foreground = if (foregroundFirst) {
                async(start = CoroutineStart.UNDISPATCHED) { engine.reschedule() }.also {
                    entered.await()
                    receiver.onReceive(context, intent)
                }
            } else {
                receiver.onReceive(context, intent)
                entered.await()
                async(start = CoroutineStart.UNDISPATCHED) { engine.reschedule() }
            }
            release.complete(Unit)
            foreground.await()
            handled.await()
            assertEquals("foregroundFirst=$foregroundFirst", 1, shadowOf(notifications).size())
            assertEquals(reminderMillis("2026-10-19T07:30"), store.scheduledAt)
            assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
            AndroidCourseReminderAlarm(context).cancel()
            notifications.cancelAll()
        }
    }

    @Test
    fun receiveRechecksRevokedPermissionsAndIgnoresImplicitOrUnrecognizedRequests() = runBlocking {
        var now = reminderMillis("2026-10-12T07:00")
        val store = SharedPreferencesCourseReminderDeliveryStore(context)
        val engine = androidEngine({ reminderState() }, store, { now })
        engine.reschedule()
        val original = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        val receiver = CourseReminderReceiver({ _, at -> engine.onAlarm(at) }, receiverScope)
        receiver.onReceive(context, Intent(original).setComponent(null))
        assertNotNull(store.scheduledAt)
        receiver.onReceive(context, Intent(original).setAction("unrecognized"))
        assertNotNull(store.scheduledAt)
        now = reminderMillis("2026-10-12T07:30")
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        receiver.onReceive(context, original)
        assertNull(store.scheduledAt)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test
    fun rescheduleReceiverAcceptsOnlyTheFiveRequiredSystemEvents() {
        val received = mutableListOf<String>()
        val receiver = CourseReminderRescheduleReceiver({ received += "reschedule" }, receiverScope)
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        for (action in actions) {
            assertTrue(CourseReminderRescheduleReceiver.handlesAction(action))
            receiver.onReceive(context, Intent(action))
        }
        receiver.onReceive(context, Intent("arbitrary.app.action"))
        receiver.onReceive(context, Intent())
        assertEquals(5, received.size)
    }

    private fun androidEngine(
        state: suspend () -> com.xmu.course.contracts.timetable.model.TimetableFeatureState,
        store: CourseReminderDeliveryStore,
        now: () -> Long,
    ) = CourseReminderEngine(
        state = state,
        ready = {
            CourseReminderSettings(context).isEnabled() && CourseReminderPermissions.notificationsGranted(context) &&
                CourseReminderPermissions.exactAlarmsGranted(context)
        },
        alarm = AndroidCourseReminderAlarm(context),
        store = store,
        post = { CourseReminderNotifications.post(context, it, now()) },
        now = now,
        zoneId = { ZoneId.of("Asia/Shanghai") },
    )
}
