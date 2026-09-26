package com.xmu.course.ui.guide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UX-11 / E2：指南页是一台“纯渲染器”。
 *
 * 章节必须全部可达，从分步教程跳过来时要直接落在对应那一章，
 * 返回键必须能用——这三件事是“教程里那个「详细教程」按钮”能不能兑现的前提。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserGuideScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(highlightSection: String?) {
        composeRule.setContent {
            MaterialTheme { UserGuideScreen(onBack = {}, highlightSection = highlightSection) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `each chapter can be landed on through its deep link`() {
        // 走产品自己的定位路径：深链接换章，七章都必须真的落到屏幕上。
        val target = mutableStateOf<String?>(null)
        composeRule.setContent {
            MaterialTheme { UserGuideScreen(onBack = {}, highlightSection = target.value) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("使用教程").assertIsDisplayed()
        GuideContent.sections.forEach { section ->
            target.value = section.id
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(600L)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("guide_section_" + section.id).assertIsDisplayed()
        }
    }

    @Test
    fun `chapter copy is interpolated instead of showing raw templates`() {
        show(highlightSection = GuideContent.SECTION_TIMETABLE)
        val section = requireNotNull(GuideContent.find(GuideContent.SECTION_TIMETABLE))
        composeRule.onNodeWithText("1. " + section.steps.first()).assertExists()
        composeRule.onNodeWithText("提示：" + section.tip).assertExists()
        composeRule.onNodeWithText("\${stepIndex + 1}. " + section.steps.first()).assertDoesNotExist()
        composeRule.onNodeWithText("提示：\${section.tip}").assertDoesNotExist()
    }

    @Test
    fun `a chip row is offered for jumping between chapters`() {
        show(highlightSection = null)
        // 芯片行本身是 LazyRow，视口外的芯片不会组合；这里只要求入口成排存在。
        composeRule.onNodeWithTag("guide_section_chips").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("guide_section_chip").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `the last chapter is not on screen by default`() {
        show(highlightSection = null)
        composeRule.onNodeWithTag("guide_section_" + GuideContent.SECTION_DATA).assertDoesNotExist()
    }

    @Test
    fun `a deep link lands on its own chapter`() {
        show(highlightSection = GuideContent.SECTION_DATA)
        composeRule.onNodeWithTag("guide_section_" + GuideContent.SECTION_DATA).assertIsDisplayed()
    }

    @Test
    fun `choosing another chapter updates selected chip and highlighted section`() {
        val first = GuideContent.sections.first()
        val target = GuideContent.sections[1]
        show(highlightSection = first.id)

        composeRule.onAllNodesWithTag("guide_section_chip")
            .get(1)
            .performSemanticsAction(SemanticsActions.OnClick) { click -> click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_section_" + target.id).assertIsDisplayed()

        composeRule.onNodeWithTag("guide_list")
            .performSemanticsAction(SemanticsActions.ScrollToIndex) { scrollToIndex -> scrollToIndex(1) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_section_chips").assertIsDisplayed()
        composeRule.onAllNodesWithTag("guide_section_chip").get(1).assertIsSelected()
        composeRule.onAllNodesWithTag("guide_section_chip").get(0).assertIsNotSelected()
    }

    @Test
    fun `back arrow leaves the guide`() {
        var backCalls = 0
        composeRule.setContent {
            MaterialTheme { UserGuideScreen(onBack = { backCalls++ }) }
        }
        composeRule.onNodeWithTag("guide_back").performClick()
        assertEquals(1, backCalls)
    }
}
