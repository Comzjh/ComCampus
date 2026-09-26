package com.xmu.course.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose-level contract for TutorialHost: manual open, step controls,
 * missing-target fail-safe, and discovery-hint completion state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val preferences = InMemoryTutorialPreferenceStore()

    private fun setHostContent(
        tutorialId: String?,
        anchors: Set<String> = ANCHORS,
        expandedLauncher: Boolean = false,
    ) {
        preferences.launcherCollapsedValue = !expandedLauncher
        composeRule.setContent {
            CompositionLocalProvider(LocalTutorialPreference provides preferences) {
                TutorialHost(tutorialId = tutorialId, modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().testTag("page-root")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("tutorial_top_toolbar"),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TutorialToolbarAction()
                        }
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            if (TutorialTargetKey.TODO_LIST in anchors) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .testTag("anchor-list")
                                        .tutorialTarget(TutorialTargetKey.TODO_LIST),
                                )
                            }
                            if (TutorialTargetKey.TODO_CHECKBOX in anchors) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .testTag("anchor-checkbox")
                                        .tutorialTarget(TutorialTargetKey.TODO_CHECKBOX),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openTutorial() {
        composeRule.onNodeWithContentDescription("展开教程入口").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("本页使用提示").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun firstRunLauncherIsCompactAndCanBeExpandedToShowTheHint() {
        setHostContent(tutorialId = "todo")
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertExists()
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertTopPositionInRootIsEqualTo(8.dp)
        composeRule.onNodeWithTag("anchor-list").assertExists()
        composeRule.onNodeWithContentDescription("本页使用提示").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("展开教程入口").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("本页使用提示").assertExists()
        composeRule.onNodeWithText("第一次用？点这里查看教程").assertExists()
    }

    @Test
    fun tappingDiscoveryHintStartsTheTutorial() {
        setHostContent(tutorialId = "todo")
        composeRule.onNodeWithContentDescription("展开教程入口").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutorial_discovery_hint")
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("第 1/3 步", substring = true).assertExists()
        composeRule.onNodeWithTag("tutorial_discovery_hint").assertDoesNotExist()
    }

    @Test
    fun openShowsFirstStepWithoutPreviousButton() {
        setHostContent(tutorialId = "todo")
        openTutorial()
        composeRule.onNodeWithText("第 1/3 步", substring = true).assertExists()
        composeRule.onNodeWithText("上一步").assertDoesNotExist()
        composeRule.onNodeWithText("下一步").assertExists()
    }

    @Test
    fun nextThenFinishClosesAndMarksCompleted() {
        setHostContent(tutorialId = "todo")
        openTutorial()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第 2/3 步", substring = true).assertExists()
        composeRule.onNodeWithText("上一步").assertExists()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("完成").assertExists()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第 1/3 步", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("本页使用提示").assertExists()
        composeRule.onNodeWithText("第一次用？点这里查看教程").assertDoesNotExist()
        assertTrue(preferences.hasCompleted("tutorial_seen_todo_v1"))
    }

    @Test
    fun previousReturnsToEarlierStep() {
        setHostContent(tutorialId = "todo")
        openTutorial()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("上一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第 1/3 步", substring = true).assertExists()
    }

    @Test
    fun skipClosesTutorialAndMarksCompleted() {
        setHostContent(tutorialId = "todo")
        openTutorial()
        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("下一步").assertDoesNotExist()
        assertTrue(preferences.hasCompleted("tutorial_seen_todo_v1"))
    }

    @Test
    fun closeIconExitsTutorial() {
        setHostContent(tutorialId = "todo")
        openTutorial()
        composeRule.onNodeWithContentDescription("关闭教程").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("下一步").assertDoesNotExist()
    }

    @Test
    fun missingTargetShowsFallbackInsteadOfCrash() {
        // 只有 TODO_LIST 有锚点；第二步 TODO_CHECKBOX 缺失时必须 fail-safe。
        setHostContent(tutorialId = "todo", anchors = setOf(TutorialTargetKey.TODO_LIST))
        openTutorial()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("当前页面没有可用教程内容").assertExists()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("完成").assertExists()
    }

    @Test
    fun completedTutorialStillShowsToolbarEntryWithoutHint() {
        preferences.markCompleted("tutorial_seen_todo_v1")
        setHostContent(tutorialId = "todo")
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertExists()
        composeRule.onNodeWithContentDescription("展开教程入口").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("本页使用提示").assertExists()
        composeRule.onNodeWithText("第一次用？点这里查看教程").assertDoesNotExist()
        assertFalse(preferences.hasCompleted("tutorial_seen_timetable_v1"))
    }

    @Test
    fun unknownTutorialIdRendersNoTutorialUi() {
        setHostContent(tutorialId = "unknown_page")
        composeRule.onNodeWithTag("page-root").assertExists()
        composeRule.onNodeWithContentDescription("本页使用提示").assertDoesNotExist()
    }

    @Test
    fun simplePageWithoutTutorialRendersNothing() {
        setHostContent(tutorialId = null)
        composeRule.onNodeWithTag("page-root").assertExists()
        composeRule.onNodeWithContentDescription("本页使用提示").assertDoesNotExist()
    }

    @Test
    fun tutorialTargetIsLayoutNoOpWithoutHost() {
        // TUT-009: 页面不在 TutorialHost 内（预览/独立测试）时，锚点必须是不改变布局的纯 no-op。
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .testTag("bare-anchor")
                    .tutorialTarget(TutorialTargetKey.TODO_LIST),
            )
        }
        composeRule.onNodeWithTag("bare-anchor")
            .assertWidthIsEqualTo(120.dp)
            .assertHeightIsEqualTo(120.dp)
    }

    // ---------------- UX-07 / D1-D3：教程入口本身 ----------------

    @Test
    fun expandedLauncherUsesAToolbarTextActionWithCollapseAction() {
        setHostContent(tutorialId = "todo", expandedLauncher = true)

        composeRule.onNodeWithTag("tutorial_fab").assertExists()
        composeRule.onNodeWithTag("tutorial_fab").assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithText("教程", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertDoesNotExist()
        // 显式收起入口：不靠长按，也不靠随机隐藏。
        composeRule.onNodeWithContentDescription("收起教程入口").assertExists()
    }

    @Test
    fun collapseShowsToolbarIconAndTapRestoresTheTextAction() {
        setHostContent(tutorialId = "todo", expandedLauncher = true)

        composeRule.onNodeWithContentDescription("收起教程入口").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertExists()

        composeRule.onNodeWithContentDescription("展开教程入口").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertDoesNotExist()
        composeRule.onNodeWithTag("tutorial_fab").assertExists()
    }

    @Test
    fun collapsingTheLauncherIsWrittenToTheStore() {
        setHostContent(tutorialId = "todo", expandedLauncher = true)
        composeRule.onNodeWithContentDescription("收起教程入口").performClick()
        composeRule.waitForIdle()
        assertTrue(preferences.launcherCollapsedValue)
    }

    @Test
    fun collapsedChoiceIsRememberedOnANewPage() {
        // 收起状态来自 store，因此换页面（新的 TutorialHost 组合）后依然是收起态。
        preferences.launcherCollapsedValue = true
        setHostContent(tutorialId = "timetable")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial_launcher_handle").assertExists()
        composeRule.onNodeWithTag("tutorial_fab").assertDoesNotExist()
    }

    @Test
    fun launcherStaysReachableWhileATutorialIsOpen() {
        // 教程开启时不重新提供入口，但也不得把入口状态丢掉。
        setHostContent(tutorialId = "todo", expandedLauncher = true)
        composeRule.onNodeWithContentDescription("收起教程入口").performClick()
        composeRule.waitForIdle()
        assertTrue(preferences.launcherCollapsedValue)
        composeRule.onNodeWithTag("tutorial_launcher_handle").performClick()
        composeRule.waitForIdle()
        assertFalse(preferences.launcherCollapsedValue)
    }

    @Test
    fun detailedGuideLinkCarriesItsOwnChapter() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalTutorialPreference provides preferences,
                LocalTutorialGuideLauncher provides { tutorialId -> opened += tutorialId },
            ) {
                TutorialHost(tutorialId = "todo", modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().testTag("page-root")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("tutorial_top_toolbar"),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TutorialToolbarAction()
                        }
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .testTag("anchor-list")
                                    .tutorialTarget(TutorialTargetKey.TODO_LIST),
                            )
                        }
                    }
                }
            }
        }
        openTutorial()
        // 「详细教程」必须存在，并且带着本章 id 去找对应的指南章节。
        composeRule.onNodeWithTag("tutorial_open_guide").assertExists()
        composeRule.onNodeWithTag("tutorial_open_guide").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("todo"), opened)
    }

    @Test
    fun guideLinkIsAbsentWithoutAGuideHost() {
        setHostContent(tutorialId = "todo")
        openTutorial()
        // 没有指南宿主时，不该画一个点了没反应的入口。
        composeRule.onNodeWithTag("tutorial_open_guide").assertDoesNotExist()
    }

    private companion object {
        val ANCHORS = setOf(TutorialTargetKey.TODO_LIST, TutorialTargetKey.TODO_CHECKBOX)
    }

    private class InMemoryTutorialPreferenceStore : TutorialPreferenceStore {
        private val completed = mutableSetOf<String>()
        var launcherCollapsedValue: Boolean = true

        override fun hasCompleted(seenKey: String): Boolean = seenKey in completed

        override fun markCompleted(seenKey: String) {
            completed.add(seenKey)
        }

        override fun isLauncherCollapsed(): Boolean = launcherCollapsedValue

        override fun setLauncherCollapsed(collapsed: Boolean) {
            launcherCollapsedValue = collapsed
        }
    }
}
