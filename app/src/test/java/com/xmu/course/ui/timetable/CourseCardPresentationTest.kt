package com.xmu.course.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.Course
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
}
