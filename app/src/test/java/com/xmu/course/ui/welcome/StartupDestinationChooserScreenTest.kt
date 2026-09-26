package com.xmu.course.ui.welcome

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.xmu.course.contracts.presentation.StartupDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupDestinationChooserScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `continue is disabled until an option is selected`() {
        composeRule.setContent {
            MaterialTheme { StartupDestinationChooserScreen(onChoose = {}) }
        }

        composeRule.onNodeWithTag("startup_choice_home").assertIsDisplayed()
        composeRule.onNodeWithTag("startup_choice_timetable").assertIsDisplayed()
        composeRule.onNodeWithText("选好后即可开始使用", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("startup_choice_continue").assertIsNotEnabled()

        composeRule.onNodeWithTag("startup_choice_timetable").performClick()
        composeRule.onNodeWithTag("startup_choice_continue").assertIsEnabled()
        composeRule.onNodeWithTag("startup_choice_continue").performClick()
    }

    @Test
    fun `choosing home option reports HOME`() {
        var chosen: StartupDestination? = null
        composeRule.setContent {
            MaterialTheme { StartupDestinationChooserScreen(onChoose = { chosen = it }) }
        }

        composeRule.onNodeWithTag("startup_choice_home").performClick()
        composeRule.onNodeWithTag("startup_choice_continue").performClick()

        assertEquals(StartupDestination.HOME, chosen)
    }
}
