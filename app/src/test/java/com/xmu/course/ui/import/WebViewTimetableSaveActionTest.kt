package com.xmu.course.ui.import

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewTimetableSaveActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveActionIsHiddenUntilTimetableIsReadyAndUsesAToolbarSizedTarget() {
        val isVisible = mutableStateOf(false)
        val isTimetableReady = mutableStateOf(false)
        var checks = 0
        composeRule.setContent {
            MaterialTheme {
                WebViewTimetableSaveAction(
                    isVisible = isVisible.value,
                    isTimetableReady = isTimetableReady.value,
                    isChecking = false,
                    isSaving = false,
                    onClick = { checks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("webview_save_timetable").assertDoesNotExist()

        composeRule.runOnIdle { isVisible.value = true }
        val action = composeRule.onNodeWithTag("webview_save_timetable")
        action.assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("重新检查课表").assertIsDisplayed()
        val height = with(composeRule.density) {
            action.fetchSemanticsNode().boundsInRoot.height.toDp()
        }
        assertTrue("save target must be at least 48dp tall", height >= 48.dp)

        action.performClick()
        assertEquals(1, checks)

        composeRule.runOnIdle { isTimetableReady.value = true }
        composeRule.onNodeWithContentDescription("保存课表").assertIsDisplayed().performClick()
        assertEquals(2, checks)
    }

    @Test
    fun saveActionAnnouncesProgressAndCannotBeTappedWhileSaving() {
        composeRule.setContent {
            MaterialTheme {
                WebViewTimetableSaveAction(
                    isVisible = true,
                    isTimetableReady = true,
                    isChecking = false,
                    isSaving = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("正在保存课表")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun recheckActionAnnouncesCheckingInsteadOfSaving() {
        composeRule.setContent {
            MaterialTheme {
                WebViewTimetableSaveAction(
                    isVisible = true,
                    isTimetableReady = false,
                    isChecking = true,
                    isSaving = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("正在检查课表")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun timetableReadinessRejectsLoginPagesAndAcceptsKnownScheduleMarkers() {
        assertFalse(isTimetableHtml("<html><body>Login</body></html>"))
        assertTrue(isTimetableHtml("<table id=\"kbckBottom\"></table>"))
        assertTrue(isTimetableHtml("<div class=\"arrage\"></div>"))
        assertFalse(isTimetableHtml(null))
    }
}
