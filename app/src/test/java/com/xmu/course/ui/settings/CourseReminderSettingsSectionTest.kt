package com.xmu.course.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CourseReminderSettingsSectionTest {
    @get:Rule val composeRule = createComposeRule()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { composeRule.mainClock.autoAdvance = true; Dispatchers.resetMain() }

    @Test
    fun soundPreferencesRequireAnExplicitUserTap() {
        var opens = 0
        composeRule.setContent {
            MaterialTheme {
                CourseReminderSectionContent(CourseReminderStatus(true, true, true), {}, {}, {},
                    onAlertPreferences = { opens++ })
            }
        }
        composeRule.onNodeWithText("默认静默；需要声音或震动时在系统中开启").assertExists()
        assertEquals(0, opens)
        composeRule.onNodeWithTag("course_reminder_alert_preferences").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun missingPermissionsArePassiveUntilTheUserTapsTheirOwnSetting() {
        var notificationRequests = 0
        var exactRequests = 0
        composeRule.setContent {
            MaterialTheme {
                CourseReminderSectionContent(CourseReminderStatus(true, false, false), {},
                    { notificationRequests++ }, { exactRequests++ })
            }
        }
        composeRule.onNodeWithTag("course_reminder_enabled").assertIsOn()
        composeRule.onAllNodesWithContentDescription("需要开启权限").assertCountEquals(2)
        assertEquals(0, notificationRequests)
        assertEquals(0, exactRequests)
        composeRule.onNodeWithTag("course_reminder_notification_permission").performClick()
        assertEquals(1, notificationRequests)
        assertEquals(0, exactRequests)
        composeRule.onNodeWithTag("course_reminder_exact_permission").performClick()
        assertEquals(1, exactRequests)
    }

    @Test
    fun switchingOffRemovesPermissionBadgesAndCanBeReenabled() {
        val status = mutableStateOf(CourseReminderStatus(true, false, false))
        composeRule.setContent {
            MaterialTheme {
                CourseReminderSectionContent(status.value, { status.value = status.value.copy(enabled = it) }, {}, {})
            }
        }
        composeRule.onNodeWithTag("course_reminder_enabled").performClick().assertIsOff()
        composeRule.onAllNodesWithContentDescription("需要开启权限").assertCountEquals(0)
        composeRule.onNodeWithTag("course_reminder_notification_permission").assertDoesNotExist()
        assertEquals(false, status.value.needsAttention)
        composeRule.onNodeWithTag("course_reminder_enabled").performClick().assertIsOn()
        composeRule.onNodeWithTag("course_reminder_notification_permission").assertExists()
    }

    @Test
    fun grantedPermissionsRemoveOnlyTheCorrespondingPrompt() {
        val status = mutableStateOf(CourseReminderStatus(true, false, false))
        // Drive the two external permission-return frames explicitly. This also checks that
        // removing a permission row does not create an actual recomposition loop.
        composeRule.mainClock.autoAdvance = false
        var compositions = 0
        composeRule.setContent {
            val current = status.value
            SideEffect { compositions++ }
            MaterialTheme { CourseReminderSectionContent(current, {}, {}, {}) }
        }
        composeRule.mainClock.advanceTimeBy(64L)
        composeRule.runOnIdle {
            Snapshot.withMutableSnapshot { status.value = status.value.copy(notificationsAllowed = true) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag("course_reminder_notification_permission").assertDoesNotExist()
        composeRule.onNodeWithTag("course_reminder_exact_permission").assertExists()
        composeRule.runOnIdle {
            Snapshot.withMutableSnapshot { status.value = status.value.copy(exactAlarmsAllowed = true) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onAllNodesWithContentDescription("需要开启权限").assertCountEquals(0)
        composeRule.onNodeWithText("已开启，按当前课表在课前 30 分钟通知。").assertExists()
        assertTrue("Permission-return UI must settle after each external update", compositions in 3..5)
    }
}
