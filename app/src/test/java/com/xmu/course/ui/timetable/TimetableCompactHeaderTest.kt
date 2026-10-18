package com.xmu.course.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.tutorial.LocalTutorialToolbarLauncher
import com.xmu.course.ui.tutorial.TutorialToolbarLauncherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableCompactHeaderTest {
    @get:Rule val composeRule = createComposeRule()

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
                        title = "第 2 周 ▼", subtitle = "2026-2027 秋冬",
                        onTitleClick = { weeksOpened++ }, onAddClick = { addOpened++ },
                        onImportClick = { importOpened++ }, onSettingsClick = { settingsOpened++ },
                        onBackToCurrent = { backToCurrent++ }, addEnabled = false,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("timetable_header").assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("timetable_header_week").performClick()
        composeRule.onNodeWithTag("timetable_header_settings").assertWidthIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("timetable_header_add").assertWidthIsAtLeast(48.dp).assertIsNotEnabled()
        composeRule.onNodeWithTag("timetable_header_back_to_current").performClick()
        composeRule.onNodeWithTag("timetable_header_more").performClick()
        composeRule.onNodeWithTag("timetable_header_import").assertIsDisplayed().performClick()
        assertEquals(1, weeksOpened)
        assertEquals(1, settingsOpened)
        assertEquals(1, importOpened)
        assertEquals(1, backToCurrent)
        assertEquals(0, addOpened)
    }

    @Test
    fun `narrow header stays one row with long semester and expanded tutorial preference`() {
        verifyNarrowHeader(compact = true, fontScale = 1.3f)
    }

    @Test
    fun `standard mode also stays one row at double font size`() {
        verifyNarrowHeader(compact = false, fontScale = 2f)
    }

    private fun verifyNarrowHeader(compact: Boolean, fontScale: Float) {
        val semester = "2026–2027学年 秋季学期 很长的演示课表名称"
        var tutorialStarts = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalTutorialToolbarLauncher provides TutorialToolbarLauncherState(
                    isCollapsed = false, showHint = true, onExpand = {}, onCollapse = {},
                    onStart = { tutorialStarts++ }, onDismissHint = {},
                ),
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        TimetableCompactHeader(
                            title = "第 17 周 ▼", subtitle = semester,
                            onTitleClick = {}, onAddClick = {}, onImportClick = {},
                            onSettingsClick = {}, onBackToCurrent = {}, compact = compact,
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("timetable_header").assertHeightIsEqualTo(if (compact) 48.dp else 56.dp)
        val header = composeRule.onNodeWithTag("timetable_header").fetchSemanticsNode().boundsInRoot
        var previousRight = header.left
        for (tag in listOf("week", "back_to_current", "settings", "add", "more")) {
            val node = composeRule.onNodeWithTag("timetable_header_$tag")
            node.assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag overlaps prior action", bounds.left >= previousRight)
            assertTrue("$tag leaves toolbar", bounds.right <= header.right && bounds.top >= header.top && bounds.bottom <= header.bottom)
            previousRight = bounds.right
        }
        composeRule.onNodeWithTag("tutorial_discovery_hint").assertDoesNotExist()
        composeRule.onNodeWithTag("timetable_header_more").performClick()
        composeRule.onNodeWithText(semester).assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_header_tutorial").assertIsDisplayed().performClick()
        assertEquals(1, tutorialStarts)
        composeRule.onNodeWithTag("timetable_header").assertHeightIsEqualTo(if (compact) 48.dp else 56.dp)
    }
}
