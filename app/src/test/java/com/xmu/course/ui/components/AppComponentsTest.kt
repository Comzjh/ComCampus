package com.xmu.course.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xmu.course.ui.theme.XmuCourseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsTitleDescriptionAndInvokesAction() {
        var clicks = 0
        composeRule.setContent {
            XmuCourseTheme(dynamicColor = false) {
                AppEmptyState(
                    title = "暂无内容",
                    description = "这是一段说明",
                    actionLabel = "去处理",
                    onAction = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("暂无内容").assertExists()
        composeRule.onNodeWithText("这是一段说明").assertExists()
        composeRule.onNodeWithText("去处理").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun emptyStateHidesOptionalPartsWhenAbsent() {
        composeRule.setContent {
            XmuCourseTheme(dynamicColor = false) {
                AppEmptyState(title = "只有标题")
            }
        }
        composeRule.onNodeWithText("只有标题").assertExists()
    }

    @Test
    fun statusChipDisplaysLabel() {
        composeRule.setContent {
            XmuCourseTheme(dynamicColor = false) {
                AppStatusChip(label = "需要登录", tone = StatusTone.Warning)
            }
        }
        composeRule.onNodeWithText("需要登录").assertExists()
    }

    @Test
    fun sectionCardDisplaysTitleAndContent() {
        composeRule.setContent {
            XmuCourseTheme(dynamicColor = false) {
                AppSectionCard(title = "学业概览") {
                    Text("概览内容")
                }
            }
        }
        composeRule.onNodeWithText("学业概览").assertExists()
        composeRule.onNodeWithText("概览内容").assertExists()
    }

    @Test
    fun groupedSectionDisplaysLabelAndRowsOnSharedSurface() {
        composeRule.setContent {
            XmuCourseTheme(dynamicColor = false) {
                AppGroupedSection(title = "外观") {
                    AppNavigationRow(title = "默认打开页面", onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("外观").assertExists()
        composeRule.onNodeWithText("默认打开页面").assertExists()
    }

    @Test
    fun navigationRowKeepsClickActionWithPressMotion() {
        var clicks = 0
        composeRule.setContent {
            XmuCourseTheme(dynamicColor = false) {
                AppNavigationRow(title = "课表设置", onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithText("课表设置").performClick()
        assertEquals(1, clicks)
    }
}
