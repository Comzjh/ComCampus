package com.xmu.course.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.TimetableConfig
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
class TimetableLargeFontGridTest {
    @get:Rule val rule = createComposeRule()

    private fun verifyGrid(fontScale: Float, weekends: Boolean) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.size(320.dp, 640.dp)) {
                        TimetableWeekGrid(
                            week = 1,
                            courses = emptyList(),
                            skippedCourseIds = emptySet(),
                            showGrid = true,
                            config = TimetableConfig(1L, showSaturday = weekends, showSunday = weekends),
                            startDate = "2026-10-26",
                            verticalScroll = rememberScrollState(),
                            onCourseClick = {},
                            onCellClick = { _, _ -> },
                        )
                    }
                }
            }
        }
        val cell = rule.onNodeWithTag("timetable_axis_cell_1").fetchSemanticsNode().boundsInRoot
        for (field in listOf("number", "start", "end")) {
            val node = rule.onNodeWithTag("timetable_axis_${field}_1", useUnmergedTree = true)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$field outside its period: $bounds / $cell", bounds.left >= cell.left &&
                bounds.right <= cell.right && bounds.top >= cell.top && bounds.bottom <= cell.bottom)
            assertOneUnclippedLine("timetable_axis_${field}_1")
        }
        rule.onNodeWithTag("timetable_axis_start_1").assertTextEquals("08:00")
        rule.onNodeWithTag("timetable_axis_end_1").assertTextEquals("08:45")
        for (day in if (weekends) 1..7 else 1..5) assertOneUnclippedLine("timetable_date_$day")
        if (weekends) rule.onNodeWithTag("timetable_date_7").assertContentDescriptionEquals("11月1日，周日")
    }

    private fun assertOneUnclippedLine(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertEquals("$tag layout", 1, layouts.size)
        assertEquals("$tag wrapped", 1, layouts.single().lineCount)
        val layout = layouts.single()
        // Unwrapped, centered paragraphs in Robolectric retain the available width;
        // their alignment offset differs from the Text node's intrinsic coordinates.
        // The complete glyph span must still fit, with no line or vertical clipping.
        assertFalse("$tag vertically clipped", layout.didOverflowHeight)
        val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        assertTrue("$tag glyphs exceed their bounds", lineWidth <= layout.size.width + 0.5f)
    }

    @Test fun `narrow grid keeps whole dates and both times at 130 percent font`() = verifyGrid(1.3f, false)
    @Test fun `seven day grid keeps dates and times at double font across a month boundary`() = verifyGrid(2f, true)
}
