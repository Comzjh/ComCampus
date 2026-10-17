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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.Course
import org.junit.Assert.assertEquals
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
    fun `transparent inactive and skipped backgrounds keep opaque high contrast text`() {
        val course = course()
        for (surface in listOf(Color.White, Color(0xFF121212))) {
            for (alpha in listOf(0f, 0.3f, 1f)) {
                for (current in listOf(false, true)) {
                    for (skipped in listOf(false, true)) {
                        val background = courseCardContainerColor(course, surface, current, alpha, skipped)
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
    }

    @Test
    fun `narrow conflict card retains configured fields within card and opens details`() {
        var opened = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme {
                    Box(Modifier.size(22.dp, 112.dp)) {
                        CourseCard(
                            course = course(),
                            modifier = Modifier.testTag("narrow_card"),
                            showLocation = true,
                            showTeacher = true,
                            compact = true,
                            onClick = { opened = true },
                        )
                    }
                }
            }
        }
        val card = rule.onNodeWithTag("narrow_card").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("course_card_name", "course_card_location", "course_card_teacher")) {
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag outside card: $bounds / $card", bounds.left >= card.left &&
                bounds.right <= card.right && bounds.top >= card.top && bounds.bottom <= card.bottom)
        }
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
