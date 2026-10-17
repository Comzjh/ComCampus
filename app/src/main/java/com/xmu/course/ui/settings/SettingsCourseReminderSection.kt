package com.xmu.course.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xmu.course.ui.components.AppGroupedSection
import com.xmu.course.ui.components.AppNavigationRow
import com.xmu.course.ui.reminder.CourseReminderPermissions
import com.xmu.course.ui.reminder.CourseReminderScheduler
import com.xmu.course.ui.reminder.CourseReminderSettings
import com.xmu.course.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CourseReminderStatus(
    val enabled: Boolean,
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    val needsAttention: Boolean get() = enabled && (!notificationsAllowed || !exactAlarmsAllowed)
}

private val reminderSettingsChanges = MutableStateFlow(0L)

private fun reminderStatus(context: Context) = CourseReminderStatus(
    enabled = CourseReminderSettings(context).isEnabled(),
    notificationsAllowed = CourseReminderPermissions.notificationsGranted(context),
    exactAlarmsAllowed = CourseReminderPermissions.exactAlarmsGranted(context),
)

/** Read-only status for the settings badge. Entering the app never requests permission. */
@Composable
internal fun rememberCourseReminderStatus(): State<CourseReminderStatus> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val changes by reminderSettingsChanges.collectAsState()
    val state = remember(context) { mutableStateOf(reminderStatus(context)) }
    LaunchedEffect(changes) { state.value = reminderStatus(context) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = reminderStatus(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
internal fun SettingsCourseReminderSection(onOpenTimetableManager: () -> Unit = {}) {
    val context = LocalContext.current
    val status by rememberCourseReminderStatus()
    val scope = rememberCoroutineScope()
    val promptHistory = remember(context) {
        context.getSharedPreferences("course_reminder_permission_ui", Context.MODE_PRIVATE)
    }
    fun refreshSchedule() {
        reminderSettingsChanges.update { it + 1 }
        scope.launch {
            try {
                CourseReminderScheduler.reschedule(context.applicationContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSchedule()
    }
    CourseReminderSectionContent(
        status = status,
        onOpenTimetableManager = onOpenTimetableManager,
        onEnabledChanged = {
            CourseReminderSettings(context).setEnabled(it)
            refreshSchedule()
        },
        onNotificationPermission = {
            val activity = context.findReminderActivity()
            val requestedBefore = runCatching { promptHistory.getBoolean("requested", false) }.getOrDefault(false)
            val runtimeMissing = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (runtimeMissing && (!requestedBefore || activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                } == true)
            ) {
                promptHistory.edit().putBoolean("requested", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }
        },
        onExactPermission = {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            }
        },
    )
}

@Composable
internal fun CourseReminderSectionContent(
    status: CourseReminderStatus,
    onEnabledChanged: (Boolean) -> Unit,
    onNotificationPermission: () -> Unit,
    onExactPermission: () -> Unit,
    onOpenTimetableManager: () -> Unit = {},
) {
    AppGroupedSection(title = "上课提醒", modifier = Modifier.testTag("course_reminder_section")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag("course_reminder_enabled")
                .toggleable(status.enabled, role = Role.Switch, onValueChange = onEnabledChanged)
                .padding(vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = AppSpacing.Md)) {
                Text("课前 30 分钟提醒", style = MaterialTheme.typography.bodyLarge)
                Text("显示课程、上课时间和已填写的地点", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(status.enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
        }
        if (status.enabled) {
            if (!status.notificationsAllowed) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                ReminderPermissionRow("开启通知", "允许发送上课提醒", "course_reminder_notification_permission", onNotificationPermission)
            }
            if (!status.exactAlarmsAllowed) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                ReminderPermissionRow("允许准时提醒", "在系统中允许“闹钟和提醒”", "course_reminder_exact_permission", onExactPermission)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            AppNavigationRow(
                title = "自建课表日期",
                description = "设置开学日期，让提醒对应真实周次",
                onClick = onOpenTimetableManager,
                modifier = Modifier.testTag("course_reminder_timetable_dates"),
            )
            Text(
                if (status.needsAttention) "开启以上权限后，按当前课表安排提醒。" else "已开启，按当前课表在课前 30 分钟通知。",
                modifier = Modifier.padding(vertical = AppSpacing.Sm),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderPermissionRow(title: String, description: String, tag: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppNavigationRow(title = title, description = description, onClick = onClick,
            modifier = Modifier.weight(1f).testTag(tag))
        Badge(modifier = Modifier.semantics { contentDescription = "需要开启权限" })
    }
}

private tailrec fun Context.findReminderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findReminderActivity()
    else -> null
}
