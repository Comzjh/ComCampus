package com.xmu.course

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.Course
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import com.xmu.course.ui.timetable.CourseCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseCardAlignmentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun namePosition(): Pair<Float, Float> {
        rule.waitForIdle()
        val position = rule.onNodeWithTag("course_card_name", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot
        return position.x to position.y
    }

    @Test
    fun textAlignmentTest() {
        val horizontal = mutableStateOf(TextHorizontalAlignment.START)
        val vertical = mutableStateOf(TextVerticalAlignment.TOP)

        rule.setContent {
            Box(Modifier.size(120.dp, 112.dp)) {
                CourseCard(
                    course = Course(
                        name = "高等数学A",
                        teacher = "教师甲",
                        location = "海韵教学楼104",
                        dayOfWeek = 1,
                        startSection = 1,
                        duration = 2,
                        weeks = (1..16).toSet(),
                    ),
                    modifier = Modifier.testTag("alignment-card"),
                    showTeacher = false,
                    showLocation = false,
                    showNote = false,
                    textHorizontalAlignment = horizontal.value,
                    textVerticalAlignment = vertical.value,
                    onClick = {},
                )
            }
        }

        val start = namePosition()
        rule.runOnIdle {
            horizontal.value = TextHorizontalAlignment.CENTER
            vertical.value = TextVerticalAlignment.CENTER
        }
        val center = namePosition()

        assertTrue("水平居中的 x 应大于靠左 x: ${start.first} -> ${center.first}", center.first > start.first)
        assertTrue("垂直居中的 y 应大于顶部 y: ${start.second} -> ${center.second}", center.second > start.second)
    }
}
