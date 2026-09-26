package com.xmu.course.ui.timetable

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableCompactHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `compact header preserves actions and gives icon actions 48dp targets`() {
        var weeksOpened = 0
        var settingsOpened = 0
        var importOpened = 0
        var addOpened = 0
        var backToCurrent = 0
        composeRule.setContent {
            Box(Modifier.width(360.dp)) {
                MaterialTheme {
                    TimetableCompactHeader(
                        title = "第 2 周 ▼",
                        subtitle = "2026-2027 秋冬",
                        onTitleClick = { weeksOpened++ },
                        onAddClick = { addOpened++ },
                        onImportClick = { importOpened++ },
                        onSettingsClick = { settingsOpened++ },
                        onBackToCurrent = { backToCurrent++ },
                        addEnabled = false,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("timetable_header_week").assertExists()
        composeRule.onNodeWithTag("timetable_header_settings").assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithTag("timetable_header_import").assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithTag("timetable_header_add").assertWidthIsAtLeast(48.dp).assertIsNotEnabled()
        composeRule.onNodeWithTag("timetable_header_week").performClick()
        composeRule.onNodeWithTag("timetable_header_settings").performClick()
        composeRule.onNodeWithTag("timetable_header_import").performClick()
        composeRule.onNodeWithTag("timetable_header_back_to_current").performClick()

        assertEquals(1, weeksOpened)
        assertEquals(1, settingsOpened)
        assertEquals(1, importOpened)
        assertEquals(1, backToCurrent)
        assertEquals(0, addOpened)
    }
}
