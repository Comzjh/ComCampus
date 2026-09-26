package com.xmu.course.ui.welcome

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 首次引导：看完、跳过都必须落到同一个入口，且中途不索要任何权限。
 *
 * 翻页本身是 `HorizontalPager` 的手势能力，单元测试只覆盖按钮这条确定路径；
 * 手势滑动在模拟器验收（G 阶段）里目视确认。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun showWelcome(onGetStarted: () -> Unit) {
        composeRule.setContent {
            MaterialTheme { WelcomeScreen(onGetStarted = onGetStarted) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `onboarding walks every page and finishes`() {
        var completed = false
        showWelcome { completed = true }

        composeRule.onNodeWithText("欢迎使用 ComCampus").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_page_indicator").assertTextEquals("1 / ${WELCOME_PAGES.size}")
        // 第一页不该有「上一页」。
        composeRule.onNodeWithTag("welcome_previous").assertDoesNotExist()

        composeRule.onNodeWithTag("welcome_learn_more").performClick()
        repeat(WELCOME_PAGES.size - 2) {
            composeRule.onNodeWithTag("welcome_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("welcome_page_indicator")
            .assertTextEquals("${WELCOME_PAGES.size} / ${WELCOME_PAGES.size}")
        composeRule.onNodeWithText(WELCOME_PAGES.last().title).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_next").assertDoesNotExist()
        composeRule.onNodeWithTag("welcome_previous").assertIsDisplayed()
        composeRule.onNodeWithText("继续设置启动页").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_finish").performClick()
        assertTrue(completed)
    }

    @Test
    fun `previous page goes back one step`() {
        showWelcome { }
        composeRule.onNodeWithTag("welcome_learn_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("welcome_previous").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("welcome_page_indicator").assertTextEquals("1 / ${WELCOME_PAGES.size}")
        composeRule.onNodeWithText("欢迎使用 ComCampus").assertIsDisplayed()
    }

    @Test
    fun `onboarding can be skipped without permissions`() {
        var completed = false
        showWelcome { completed = true }
        composeRule.onNodeWithTag("welcome_learn_more").performClick()
        composeRule.onNodeWithTag("welcome_skip").performClick()
        assertTrue(completed)
    }

    @Test
    fun `welcome offers immediate entry without walking all pages`() {
        var starts = 0
        showWelcome { starts++ }
        composeRule.onNodeWithTag("welcome_start").performClick()
        assertEquals(1, starts)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h568dp", fontScale = 1.5f)
    fun `footer text remains completely inside short viewport at large font`() {
        showWelcome { }
        composeRule.onNodeWithTag("welcome_learn_more").performClick()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        listOf("跳过引导", "下一页", "上一页").forEach { label ->
            val bounds = composeRule.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$label must fit vertically", bounds.top >= root.top && bounds.bottom <= root.bottom)
            assertTrue("$label must fit horizontally", bounds.left >= root.left && bounds.right <= root.right)
        }
    }

    @Test
    fun `guide pages match the product chapters`() {
        assertEquals(7, WELCOME_PAGES.size)
        assertEquals(
            listOf("欢迎使用 ComCampus", "首页", "课表", "待办", "学业", "学业模拟", "隐藏功能与教程"),
            WELCOME_PAGES.map { it.title },
        )
    }

    @Test
    fun `academic pages do not promise an import flow`() {
        val forbidden = listOf("导入成绩", "Excel", "excel", "PDF", "pdf", "未导入")
        WELCOME_PAGES.filter { it.title in listOf("学业", "学业模拟") }.forEach { page ->
            (listOf(page.title, page.body) + page.tips).forEach { text ->
                forbidden.forEach { word ->
                    assertTrue("「${page.title}」含有过期说法「$word」：$text", !text.contains(word))
                }
            }
        }
    }
}
