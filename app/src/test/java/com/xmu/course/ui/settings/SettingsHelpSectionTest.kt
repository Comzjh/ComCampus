package com.xmu.course.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.SettingsDataSource
import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.contracts.presentation.StartupDestinationPreference
import com.xmu.course.domain.Semester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E6：指南不是看完就扔的一次性资源。
 *
 * 设置里必须常驻两个入口——重看新手引导、打开完整功能指南。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsHelpSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var replayCalls = 0
    private var openGuideCalls = 0
    private val assignmentSettings = StubAssignmentSettings()
    private val startupPreference = StubStartupDestinationPreference()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun showSettings(
        semesters: List<Semester> = listOf(Semester(id = 7L, code = "20261", name = "2026-2027 秋季学期")),
    ) {
        val viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext(),
            StubSettingsDataSource(semesters),
            assignmentSettings,
            StubTodoAutoSyncSettings(),
            startupPreference,
        )
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onReplayGuide = { replayCalls++ },
                    onOpenGuide = { openGuideCalls++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapRow(tag: String) {
        // 与删除按钮同理：小视口里按坐标注入的点击会落在视口下沿，直接触发 OnClick 语义。
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `switch title identifies one operable row with checked state`() {
        showSettings()
        val row = composeRule.onNodeWithText("自动导入畅课作业")
        row.performScrollTo().assertHasClickAction().assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        row.performSemanticsAction(SemanticsActions.OnClick)
        row.assertIsOn()
        assertEquals(true, assignmentSettings.enabled)
        row.performSemanticsAction(SemanticsActions.OnClick)
        row.assertIsOff()
        assertEquals(false, assignmentSettings.enabled)
    }

    @Test
    fun `startup choices expose mutually exclusive selected state`() {
        showSettings()
        val home = composeRule.onNodeWithTag("settings_startup_home")
        val timetable = composeRule.onNodeWithTag("settings_startup_timetable")
        home.performScrollTo().assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        timetable.assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        tapRow("settings_startup_timetable")
        timetable.assertIsSelected()
        home.assertIsNotSelected()
        assertEquals(StartupDestination.TIMETABLE, startupPreference.saved)
        tapRow("settings_startup_home")
        home.assertIsSelected()
        timetable.assertIsNotSelected()
        assertEquals(StartupDestination.HOME, startupPreference.saved)
    }

    @Test
    fun `help section offers both guide entries`() {
        showSettings()
        composeRule.onNodeWithText("帮助与教程").performScrollTo().assertExists()
        composeRule.onNodeWithTag("settings_replay_guide").assertExists()
        composeRule.onNodeWithTag("settings_open_feature_guide").assertExists()
    }

    @Test
    fun `each help row invokes its own action`() {
        showSettings()
        tapRow("settings_replay_guide")
        assertEquals(1, replayCalls)
        assertEquals(0, openGuideCalls)

        tapRow("settings_open_feature_guide")
        assertEquals(1, replayCalls)
        assertEquals(1, openGuideCalls)
    }

    @Test
    fun `about section no longer hides the replay button`() {
        showSettings()
        // 「重新查看首次使用引导」已经收进帮助分区，不该在两处各留一个入口。
        assertEquals(0, composeRule.onAllNodesWithText("重新查看首次使用引导").fetchSemanticsNodes().size)
    }

    @Test
    fun `about section identifies the current open source license`() {
        showSettings()
        composeRule.onNodeWithText("开源协议：GNU GPL-3.0-only")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `semester section clarifies that enrolled courses need separate timetable import`() {
        showSettings()

        composeRule.onNodeWithText("当前课表学期").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("学业课程与本机课表独立，需要另行导入。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `empty timetable state asks for timetable import in plain words`() {
        showSettings(semesters = emptyList())

        composeRule.onNodeWithText("当前课表学期").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("本机还没有课表，请先导入课表").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("学业课程与本机课表独立，需要另行导入。")
            .performScrollTo()
            .assertIsDisplayed()
    }
}

private class StubSettingsDataSource(initialSemesters: List<Semester>) : SettingsDataSource {
    private val semesters = MutableStateFlow(initialSemesters)

    override fun observeSemesters(): Flow<List<Semester>> = semesters

    override suspend fun updateSemesterStartDate(semesterId: Long, startDate: String) = Unit

    override suspend fun deleteSemesterCourses(semesterId: Long) = Unit

    override suspend fun deleteSemester(semesterId: Long) = Unit
}

private class StubAssignmentSettings : AssignmentSyncSettings {
    var enabled = false
    override fun isAutoImportEnabled(): Boolean = enabled
    override fun setAutoImportEnabled(enabled: Boolean) { this.enabled = enabled }
}

private class StubTodoAutoSyncSettings : TodoAutoSyncSettings {
    override fun isEnabled(): Boolean = true
    override fun setEnabled(enabled: Boolean) = Unit
    override fun lastSuccessfulSyncAt(): Long? = null
    override fun setLastSuccessfulSyncAt(timestamp: Long) = Unit
}

private class StubStartupDestinationPreference : StartupDestinationPreference {
    var saved: StartupDestination? = null
    override fun destination(): StartupDestination? = saved
    override fun setDestination(destination: StartupDestination) { saved = destination }
}
