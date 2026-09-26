package com.xmu.course.ui.timetable

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddCourseDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `表单正文可滚动且底部操作仍可达`() {
        var dismissCount = 0
        composeRule.setContent {
            MaterialTheme {
                AddCourseDialog(onDismiss = { dismissCount++ }, onSave = {})
            }
        }

        composeRule.onNodeWithTag("add_course_dialog_scroll_content")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithText("周数（如 1-16周 或 1-3单周,6,8-9）")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("取消").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, dismissCount)
    }

    @Test
    fun `保存按钮仍执行原有必填校验`() {
        var saveCount = 0
        composeRule.setContent {
            MaterialTheme {
                AddCourseDialog(onDismiss = {}, onSave = { saveCount++ })
            }
        }

        composeRule.onNodeWithText("保存").performClick()

        composeRule.onNodeWithText("请检查输入：名称必填、节次 1-${TimeTableConfig.sectionCount}、周数格式合法")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(0, saveCount)
    }
}
