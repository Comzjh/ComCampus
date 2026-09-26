package com.xmu.course.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.SettingsDataSource
import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.contracts.presentation.StartupDestinationPreference
import com.xmu.course.domain.Semester
import com.xmu.course.ui.tutorial.LocalTutorialPreference
import com.xmu.course.ui.tutorial.TutorialPreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * POL-003 防呆回归：删除本学期课表必须是独立「危险操作」分区，
 * 并且永远经过二次确认；取消不删，确认才删，且只删本机数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDestructiveActionTest {

    private fun <T> SemanticsConfiguration.lookup(key: SemanticsPropertyKey<T>): T? =
        if (contains(key)) get(key) else null


    @get:Rule
    val composeRule = createComposeRule()

    private val source = RecordingSettingsDataSource()
    private var expectedErrorColor = Color.Unspecified

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun showSettings() {
        val viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext(),
            source,
            ToggleAssignmentSettings(),
            ToggleTodoAutoSyncSettings(),
            RecordingStartupDestinationPreference(),
        )
        composeRule.setContent {
            MaterialTheme {
                expectedErrorColor = MaterialTheme.colorScheme.error
                SettingsScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun showSettingsWithExpandedTutorialLauncher() {
        val viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext(),
            source,
            ToggleAssignmentSettings(),
            ToggleTodoAutoSyncSettings(),
            RecordingStartupDestinationPreference(),
        )
        val tutorialPreferences = object : TutorialPreferenceStore {
            override fun hasCompleted(seenKey: String) = false
            override fun markCompleted(seenKey: String) = Unit
            override fun isLauncherCollapsed() = false
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalTutorialPreference provides tutorialPreferences) {
                MaterialTheme {
                    expectedErrorColor = MaterialTheme.colorScheme.error
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * 打开二次确认对话框。
     *
     * 320x470 的测试视口里 `bringIntoView` 只会把按钮顶到视口下沿，
     * 按坐标注入的点击会落在那条沿上；这里先滚动到分区标题，再直接触发
     * 按钮自身的 OnClick 语义动作，命中路径与用户点击等价且不受视口高度影响。
     */
    private fun openDeleteDialog() {
        composeRule.onNodeWithText("危险操作").performScrollTo()
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithText(DELETE_LABEL)
        row.performScrollTo()
        composeRule.waitForIdle()
        row.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun destructiveActionLivesInItsOwnSection() {
        showSettings()

        composeRule.onNodeWithText("危险操作").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(DELETE_LABEL).performScrollTo().assertIsDisplayed()
        // 常规导航行仍然留在自己的分区里，不与删除按钮共用一张卡。
        composeRule.onNodeWithText("课表管理").assertIsDisplayed()
        composeRule.onNodeWithText("课程管理").assertIsDisplayed()
    }

    @Test
    fun tutorialActionStaysInSettingsHeaderAndDeleteRemainsReachable() {
        showSettingsWithExpandedTutorialLauncher()
        composeRule.onNodeWithTag("tutorial_fab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("收起教程入口").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        val title = composeRule.onNodeWithText("设置").fetchSemanticsNode().boundsInRoot
        val tutorialAction = composeRule.onNodeWithTag("tutorial_fab").fetchSemanticsNode().boundsInRoot
        assertNoOverlap(title, tutorialAction)

        composeRule.onNodeWithText(DELETE_LABEL).performScrollTo().assertIsDisplayed()
    }

    private fun assertNoOverlap(first: Rect, second: Rect) {
        val overlaps = first.left < second.right && first.right > second.left &&
            first.top < second.bottom && first.bottom > second.top
        assertTrue("操作项 $first 与教程入口 $second 不应重叠", !overlaps)
    }

    @Test
    fun deleteRequiresConfirmationAndCancelDeletesNothing() {
        showSettings()

        openDeleteDialog()
        // AlertDialog 把内容合并进一个语义节点，逐项文案必须在未合并树里找。
        composeRule.onNodeWithText("确认删除", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(DIALOG_COPY, useUnmergedTree = true).assertExists()

        composeRule.onNodeWithText("取消", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(0, source.deleteCourseCalls)
        assertEquals(0, source.deleteSemesterCalls)
        composeRule.onNodeWithText(DIALOG_COPY, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun confirmedDeleteTargetsCurrentSemesterOnly() {
        showSettings()

        openDeleteDialog()
        composeRule.onNodeWithText("删除", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(1, source.deleteCourseCalls)
        assertEquals(0, source.deleteSemesterCalls)
        assertEquals(7L, source.lastDeletedSemesterId)
    }

    @Test
    fun finalDeleteConfirmationUsesDestructiveTextColor() {
        showSettings()
        openDeleteDialog()
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("删除", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("删除操作应有可见文字布局", layouts.isNotEmpty())
        assertEquals(expectedErrorColor, layouts.single().layoutInput.style.color)
        assertEquals(0, source.deleteCourseCalls)
    }

    private companion object {
        const val DELETE_LABEL = "删除当前学期课表"
        const val DIALOG_COPY = "将删除本机保存的本学期全部课程，不会修改学校系统中的数据，也无法从应用内恢复。"
    }

    private class RecordingSettingsDataSource : SettingsDataSource {
        val semesters = MutableStateFlow(listOf(Semester(id = 7L, code = "20261", name = "2026-2027 秋季学期")))
        var deleteCourseCalls = 0
        var deleteSemesterCalls = 0
        var lastDeletedSemesterId = -1L

        override fun observeSemesters(): Flow<List<Semester>> = semesters

        override suspend fun updateSemesterStartDate(semesterId: Long, startDate: String) = Unit

        override suspend fun deleteSemesterCourses(semesterId: Long) {
            deleteCourseCalls++
            lastDeletedSemesterId = semesterId
        }

        override suspend fun deleteSemester(semesterId: Long) {
            deleteSemesterCalls++
            lastDeletedSemesterId = semesterId
        }
    }

    private class ToggleAssignmentSettings : AssignmentSyncSettings {
        override fun isAutoImportEnabled(): Boolean = false
        override fun setAutoImportEnabled(enabled: Boolean) = Unit
    }

    private class ToggleTodoAutoSyncSettings : TodoAutoSyncSettings {
        override fun isEnabled(): Boolean = true
        override fun setEnabled(enabled: Boolean) = Unit
        override fun lastSuccessfulSyncAt(): Long? = null
        override fun setLastSuccessfulSyncAt(timestamp: Long) = Unit
    }

    private class RecordingStartupDestinationPreference : StartupDestinationPreference {
        override fun destination(): StartupDestination? = null
        override fun setDestination(destination: StartupDestination) = Unit
    }
}
