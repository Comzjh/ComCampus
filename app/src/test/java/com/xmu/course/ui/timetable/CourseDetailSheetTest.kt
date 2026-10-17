package com.xmu.course.ui.timetable

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.domain.Course
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseDetailSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `窄屏八种颜色均完整显示并提供可访问选择状态`() {
        var changedColor: String? = null
        composeRule.setContent {
            MaterialTheme {
                var selected by remember { mutableStateOf("#faac8f") }
                Box(Modifier.width(360.dp).padding(horizontal = 20.dp)) {
                    CourseDetailColorSelector(
                        selectedColor = selected,
                        onColorChange = { selected = it; changedColor = it },
                    )
                }
            }
        }
        val labels = listOf("珊瑚橙", "浅杏色", "草绿色", "湖蓝色", "淡紫色", "粉红色", "天蓝色", "金黄色")
        val groupBounds = composeRule.onNodeWithTag("course_detail_colors").getUnclippedBoundsInRoot()
        val swatchBounds = labels.map { label ->
            val node = composeRule.onNodeWithContentDescription("课程颜色：$label")
            node.assertIsDisplayed().assertHasClickAction()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$label 点击区至少48dp", bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp)
            assertTrue("$label 不超出横向内容区域", bounds.left >= groupBounds.left && bounds.right <= groupBounds.right)
            bounds
        }
        assertTrue("窄屏色板应换行", swatchBounds.map { it.top }.distinct().size > 1)
        composeRule.onNodeWithContentDescription("课程颜色：珊瑚橙").assertIsSelected()
        composeRule.onNodeWithContentDescription("课程颜色：金黄色").assertIsNotSelected().performClick()
        composeRule.onNodeWithContentDescription("课程颜色：金黄色").assertIsSelected()
        composeRule.onNodeWithContentDescription("课程颜色：珊瑚橙").assertIsNotSelected()
        assertEquals("#E8C877", changedColor)
    }

    @Test
    fun `匹配到畅课课程时详情显示畅课信息`() {
        val tronCourseMatch = TimetableMatchModel(
            name = "高等数学",
            semester = "2026-2027秋季",
            instructor = "畅课教师",
        )
        val localCourse = Course(
            id = 7L,
            name = "高等数学（1）",
            teacher = "本地教师",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = setOf(1),
        )

        composeRule.setContent {
            MaterialTheme {
                CourseDetailSheet(
                    course = localCourse,
                    tronCourseMatch = tronCourseMatch,
                    isSkipped = false,
                    onDismiss = {},
                    onToggleSkipped = {},
                    onColorChange = {},
                    onNoteChange = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("畅课").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("畅课教师").assertIsDisplayed()
        composeRule.onNodeWithText("2026-2027秋季").assertIsDisplayed()
        composeRule.onNodeWithText("课程资料、作业、通知入口将在后续版本接入").assertIsDisplayed()
    }
}
