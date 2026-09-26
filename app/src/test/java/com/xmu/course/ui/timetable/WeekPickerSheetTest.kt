package com.xmu.course.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekPickerSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedWeekAndActualWeekAreExposedToTalkBack() {
        val selectedWeek = mutableIntStateOf(3)
        var dismissCount = 0
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    WeekPickerSheetContent(
                        totalWeeks = 20,
                        viewWeek = selectedWeek.intValue,
                        actualWeek = 5,
                        onSelect = { selectedWeek.intValue = it },
                        onBackToCurrent = { selectedWeek.intValue = 5 },
                        onDismiss = { dismissCount += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("week_picker_legend")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("week_picker_week_3")
            .assertIsSelected()
            .assertHasClickAction()
            .assertContentDescriptionEquals("第3周，当前选中周")
        composeRule.onNodeWithTag("week_picker_week_5")
            .assertIsNotSelected()
            .assertContentDescriptionEquals("第5周，本周")
            .performClick()

        assertEquals(5, selectedWeek.intValue)
        assertEquals(1, dismissCount)
        composeRule.onNodeWithTag("week_picker_week_5")
            .assertIsSelected()
            .assertContentDescriptionEquals("第5周，当前选中周，本周")
    }
}
