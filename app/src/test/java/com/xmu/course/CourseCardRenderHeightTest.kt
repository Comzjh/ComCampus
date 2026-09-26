package com.xmu.course

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.Course
import com.xmu.course.ui.timetable.CourseCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CARD_TEST_TAG = "layout-bug-card"

/**
 * 直接验证 Compose 渲染层：卡片背景必须填满布局引擎分配的完整高度。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseCardRenderHeightTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fourSectionCourseRenderHeight() {
        val course = Course(
            name = "大学物理实验(08)",
            teacher = "陈婷",
            location = "海韵教学楼104",
            dayOfWeek = 4,
            startSection = 5,
            duration = 4,
            weeks = (1..16).toSet(),
        )
        var expectedPx = 0
        rule.setContent {
            val cellHeight = 64.dp
            expectedPx = with(LocalDensity.current) { (cellHeight * 4).roundToPx() }
            Box(Modifier.width(80.dp).height(cellHeight * 4)) {
                CourseCard(
                    course = course,
                    modifier = Modifier.testTag(CARD_TEST_TAG),
                    showTeacher = true,
                    showLocation = true,
                    showNote = false,
                ) {}
            }
        }
        rule.waitForIdle()
        val actualPx = rule.onNodeWithTag(CARD_TEST_TAG).fetchSemanticsNode().size.height
        assertEquals("duration=4 的课程卡必须填满 cellHeight x 4", expectedPx, actualPx)
    }

    @Test
    fun compactCardKeepsCourseBasics() {
        val course = Course(
            name = "大学物理实验（08）",
            teacher = "陈婷",
            location = "海韵教学楼104",
            dayOfWeek = 1,
            startSection = 1,
            duration = 4,
            weeks = (1..16).toSet(),
        )
        rule.setContent {
            Box(Modifier.width(80.dp).height(64.dp * 4)) {
                CourseCard(
                    course = course,
                    compact = true,
                    showTeacher = true,
                    showLocation = true,
                    showNote = false,
                    onClick = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("@海韵教学楼104", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("陈婷", useUnmergedTree = true).assertExists()
    }
}
