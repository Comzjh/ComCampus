package com.xmu.course.ui.timetable

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmu.course.domain.Course
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseCardPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun courseCardForegroundAdaptsToLightAndDarkColors() {
        assertEquals(
            Color(0xFF0B1117),
            courseCardForegroundColor(Color(0xFFFDCF93)),
        )
        assertEquals(
            Color.White,
            courseCardForegroundColor(Color(0xFF1A5CAB)),
        )
    }

    @Test
    fun syntheticCourseCardRetainsItsContentAndClickAction() {
        val course = Course(
            name = "数据可视化",
            teacher = "演示教师",
            location = "教学楼 A103",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = setOf(1),
            color = "#8FBCFA",
        )
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(136.dp),
                ) {
                    CourseCard(
                        course = course,
                        modifier = Modifier.testTag("synthetic_course_card"),
                        textSize = 11,
                        showNote = false,
                        onClick = { clicks++ },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("数据可视化").assertExists()
        composeRule.onNodeWithText("@教学楼 A103").assertExists()
        composeRule.onNodeWithTag("synthetic_course_card").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun statesUseIndependentMarkersAndShareTypographyInNormalAndCompactCards() {
        val course = Course(
            name = "数据可视化", teacher = "演示教师", location = "教学楼 A103",
            dayOfWeek = 1, startSection = 1, duration = 2, weeks = setOf(1), color = "#8FBCFA",
        )
        val states = listOf(true to false, false to false, true to true, false to true)
        val descriptions = listOf("本周", "非本周", "本周，已标记翘课", "非本周，已标记翘课")
        val labels = listOf("", "非本周", "翘课", "非本周 · 翘课")
        val clicks = IntArray(states.size)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    states.indices.chunked(2).forEach { indices ->
                        Row {
                            indices.forEach { index ->
                                Box(Modifier.width(120.dp).height(192.dp)) {
                                    CourseCard(
                                        course = course,
                                        modifier = Modifier.testTag("state_card_$index"),
                                        isCurrentWeek = states[index].first,
                                        isSkipped = states[index].second,
                                        textSize = 11,
                                        textHorizontalAlignment = TextHorizontalAlignment.START,
                                        textVerticalAlignment = TextVerticalAlignment.TOP,
                                        compact = index % 2 == 1,
                                        onClick = { clicks[index]++ },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        var expectedTextColor: Color? = null
        states.indices.forEach { index ->
            val rootTag = "state_card_$index"
            val root = composeRule.onNodeWithTag(rootTag)
            root.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, descriptions[index]))
            val title = composeRule.onNode(
                hasTestTag("course_card_name") and hasAnyAncestor(hasTestTag(rootTag)),
                useUnmergedTree = true,
            )
            title.assertTextEquals(course.name)
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val style = layouts.single().layoutInput.style
            assertEquals(11.sp, style.fontSize)
            assertEquals(FontWeight.SemiBold, style.fontWeight)
            assertEquals(TextAlign.Start, style.textAlign)
            if (expectedTextColor == null) expectedTextColor = style.color
            assertEquals(expectedTextColor, style.color)
            if (labels[index].isNotEmpty()) {
                composeRule.onNode(
                    hasTestTag("course_card_status") and hasAnyAncestor(hasTestTag(rootTag)),
                    useUnmergedTree = true,
                ).assertTextEquals(labels[index])
            }
            for ((tag, expected) in listOf(
                "course_card_inactive_marker" to !states[index].first,
                "course_card_skipped_marker" to states[index].second,
            )) {
                val marker = composeRule.onNode(
                    hasTestTag(tag) and hasAnyAncestor(hasTestTag(rootTag)),
                    useUnmergedTree = true,
                )
                if (expected) marker.assertExists() else marker.assertDoesNotExist()
            }
            root.performScrollTo().performClick()
            assertEquals(1, clicks[index])
        }
    }
}
