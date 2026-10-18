package com.xmu.course.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseCardVisualRegressionTest {
    @get:Rule val rule = createComposeRule()

    private fun course() = Course(
        name = "高等数学课程名称较长",
        teacher = "教师名称较长",
        location = "海韵教学楼很长的教室名称104",
        dayOfWeek = 1,
        startSection = 1,
        duration = 2,
        weeks = (1..16).toSet(),
        color = "#8FBCFA",
    )

    @Test
    fun `skipped cards are neutral gray in either week while normal cards retain color and contrast`() {
        val course = course()
        for (surface in listOf(Color.White, Color(0xFF1C1C1E))) {
            for (alpha in listOf(0f, 0.3f, 1f)) {
                val normalBackground = courseCardContainerColor(course, surface, true, alpha, false)
                val skippedBackground = courseCardContainerColor(course, surface, true, alpha, true)
                if (alpha > 0f) assertNotEquals(normalBackground, skippedBackground)
                for (current in listOf(false, true)) {
                    for (skipped in listOf(false, true)) {
                        val background = courseCardContainerColor(course, surface, current, alpha, skipped)
                        assertEquals(if (skipped) skippedBackground else normalBackground, background)
                        if (skipped) {
                            val channels = listOf(background.red, background.green, background.blue)
                            assertTrue("Skipped card must stay neutral gray", channels.max() - channels.min() < 0.01f)
                            val otherColor = course.copy(color = "#F49BC1")
                            assertEquals(background, courseCardContainerColor(otherColor, surface, current, alpha, true))
                        }
                        assertEquals(alpha, courseCardRenderAlpha(current, alpha, skipped), 0f)
                        val foreground = courseCardForegroundColor(background)
                        val light = maxOf(background.luminance(), foreground.luminance())
                        val dark = minOf(background.luminance(), foreground.luminance())
                        assertTrue((light + 0.05f) / (dark + 0.05f) >= 4.5f)
                        assertEquals(1f, foreground.alpha, 0f)
                    }
                }
            }
        }
        assertEquals("#8FBCFA", course.color)
        assertEquals(courseCardColor(course).desaturateForRender(),
            courseCardContainerColor(course, Color.White, true, 1f, false))
        // Toggling the render state never changes the saved course color.
        courseCardContainerColor(course, Color.White, true, 1f, true)
        assertEquals(courseCardColor(course).desaturateForRender(),
            courseCardContainerColor(course, Color.White, true, 1f, false))
    }

    @Test
    fun `narrow conflict card retains configured fields within card and opens details`() {
        var opened = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme {
                    Box(Modifier.size(22.dp, 128.dp)) {
                        CourseCard(
                            course = course(),
                            modifier = Modifier.testTag("narrow_card"),
                            showLocation = true,
                            showTeacher = true,
                            isCurrentWeek = false,
                            isSkipped = true,
                            compact = true,
                            onClick = { opened = true },
                        )
                    }
                }
            }
        }
        val card = rule.onNodeWithTag("narrow_card").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("course_card_name", "course_card_location", "course_card_teacher", "course_card_status_marker")) {
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag outside card: $bounds / $card", bounds.left >= card.left &&
                bounds.right <= card.right && bounds.top >= card.top && bounds.bottom <= card.bottom)
        }
        for (tag in listOf("course_card_name", "course_card_location", "course_card_teacher")) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$tag must ellipsize within its height", !layouts.single().didOverflowHeight)
            if (tag != "course_card_name") assertEquals(1, layouts.single().lineCount)
        }
        rule.onNodeWithTag("narrow_card").assertContentDescriptionEquals(
            "高等数学课程名称较长，海韵教学楼很长的教室名称104，教师名称较长",
        )
        rule.onNodeWithTag("narrow_card").performClick()
        assertTrue(opened)
    }
    @Test
    fun `single period at double font size reserves a visible course name first`() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    Box(Modifier.size(54.dp, 50.dp)) {
                        CourseCard(course = course().copy(duration = 1), textSize = 12, onClick = {})
                    }
                }
            }
        }
        val name = rule.onNodeWithTag("course_card_name", useUnmergedTree = true)
        name.assertIsDisplayed()
        name.assertHeightIsAtLeast(24.dp)
    }

}
