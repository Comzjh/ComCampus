package com.xmu.course.ui.academic

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.LocalAcademicSnapshotParser
import com.xmu.course.data.academiccompletion.SnapshotImportResult
import com.xmu.course.ui.academiccompletion.AcademicCompletionViewModel
import com.xmu.course.ui.scrollUntilComposed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 「本学期」页 UI 测试（合成数据）。
 *
 * 学分补录从培养方案页搬来这里：官方来源值与本地确认值必须分行呈现，
 * 参考说明只展示、绝不自动填入，取消/校验失败都不产生任何写入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicSemesterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val validJson = """
        {
          "generated_at": "2026-09-19",
          "semester": {"jwapp_code": "2026-2027-1"},
          "plan": {
            "name": "合成方案", "required_xf": 160, "earned_xf_snapshot": 40,
            "snapshot_czsj": "2026-09-16 22:09",
            "this_semester_selected_xf_plan_level": 3
          },
          "courses": [
            {"KCDM":"C001","KCMC":"合成课程甲","XF":3,"in_plan":true,"xf_status":"confirmed","JSXM":"合成教师","BJMC":"01"},
            {"KCDM":"C002","KCMC":"合成课程乙","XF":null,"in_plan":false,"xf_status":"需人工确认","note":"合成参考说明"}
          ],
          "faw_completed_courses": [],
          "totals": {"in_plan_xf_sum": 3}
        }
    """.trimIndent()

    private fun newStore(imported: Boolean = true): AcademicCompletionStore {
        val store = AcademicCompletionStore(File(tempFolder.root, "snapshot.json"))
        if (imported) {
            val parsed = LocalAcademicSnapshotParser.parse(validJson)
            assertTrue(parsed is SnapshotImportResult.Success)
            store.import((parsed as SnapshotImportResult.Success).snapshot)
        }
        return store
    }

    private fun showContent(imported: Boolean = true): AcademicCompletionViewModel {
        val viewModel = AcademicCompletionViewModel(
            newStore(imported),
            UnconfinedTestDispatcher(mainDispatcher.scheduler),
        )
        composeRule.setContent {
            AcademicSemesterScreen(viewModel = viewModel, onBack = {})
        }
        return viewModel
    }

    private fun loadedSnapshot(
        viewModel: AcademicCompletionViewModel,
    ): AcademicCompletionSnapshot =
        (viewModel.uiState.value.storeState as AcademicCompletionStore.State.Loaded).snapshot

    @Test
    fun emptyStatePointsToAcademicRefresh() {
        showContent(imported = false)
        composeRule.onNodeWithTag("academic_semester_empty").assertIsDisplayed()
        composeRule.onNodeWithText("本学期数据尚未刷新").assertIsDisplayed()
"回到「学业」页点击刷新，即可从教务获取本学期课程。"
    }

    @Test
    fun enrolledRowsShowOfficialFactsAndPendingFillEntry() {
        showContent()
        composeRule.onNodeWithTag("academic_semester_header").assertIsDisplayed()
"在修 2 门 · 学校页面统计时间 2026-09-16 22:09"
        composeRule.onNodeWithText("本机获取时间 未记录").assertIsDisplayed()
        // AppStatusChip 是容器，文本位于子节点：按片段匹配
        composeRule.onNodeWithText("学分待确认 1 门", substring = true).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasText("合成课程甲")).assertIsDisplayed()
        composeRule.onNodeWithTag("academic_completion_credits_C001").assertHasText("3 学分")
        // 官方对方案外在修课不给学分：不猜测、不自动填，只留下用户确认入口
        composeRule.scrollUntilComposed(hasTestTag("academic_completion_fill_C002")).assertIsDisplayed()
        composeRule.onNodeWithText("来源暂无学分", substring = true).assertIsDisplayed()
    }

    @Test
    fun creditDialogStartsEmptyShowsHintAndWritesLocalOverrideOnly() {
        val viewModel = showContent()
        composeRule.scrollUntilComposed(hasTestTag("academic_completion_fill_C002")).performClick()
        // 参考说明展示，但输入框必须为空（绝不自动采用参考值）
        composeRule.onNodeWithTag("academic_completion_credit_field").assertEditableEmpty()
        composeRule.onNodeWithTag("academic_completion_credit_hint").assertHasText("合成参考说明")
        composeRule.onNodeWithTag("academic_completion_credit_field").performTextInput("2.0")
        composeRule.onNodeWithTag("academic_completion_credit_submit").performClick()
        composeRule.waitForIdle()

        val snapshot = loadedSnapshot(viewModel)
        assertNull(snapshot.enrolledCourses.first { it.courseCode == "C002" }.creditsText)
        assertEquals("2", snapshot.localOverrides.getValue("C002").creditsText)
        // 写入后必须标明这是本地确认值，且对话框已关闭
        composeRule.scrollUntilComposed(hasText("本地确认")).assertIsDisplayed()
        composeRule.onNodeWithTag("academic_completion_chip_local_C002").assertExists()
        composeRule.onNodeWithTag("academic_completion_credit_field").assertDoesNotExist()
    }

    @Test
    fun creditDialogCancelWritesNothing() {
        val viewModel = showContent()
        composeRule.scrollUntilComposed(hasTestTag("academic_completion_fill_C002")).performClick()
        composeRule.onNodeWithTag("academic_completion_credit_field").assertEditableEmpty()
        composeRule.onNodeWithTag("academic_completion_credit_field").performTextInput("7")
        composeRule.onNodeWithTag("academic_completion_credit_cancel").performClick()
        composeRule.waitForIdle()

        assertTrue(loadedSnapshot(viewModel).localOverrides.isEmpty())
        assertNull(viewModel.uiState.value.creditDialogCourse)
        composeRule.scrollUntilComposed(hasTestTag("academic_completion_fill_C002")).assertIsDisplayed()
    }

    @Test
    fun invalidCreditShowsErrorInlineWithoutSaving() {
        val viewModel = showContent()
        composeRule.scrollUntilComposed(hasTestTag("academic_completion_fill_C002")).performClick()
        composeRule.onNodeWithTag("academic_completion_credit_field").performTextInput("11")
        composeRule.onNodeWithTag("academic_completion_credit_submit").performClick()
        composeRule.waitForIdle()
        // 对话框不关闭，错误内联展示，且不产生任何写入
        composeRule.onNodeWithTag("academic_completion_credit_field").assertExists()
        composeRule.onNodeWithText("单门课程学分不能超过 10").assertIsDisplayed()
        assertTrue(loadedSnapshot(viewModel).localOverrides.isEmpty())
    }

    @Test
    fun officialCourseFactsAreNeverRewrittenByLocalConfirmation() {
        val viewModel = showContent()
        val before = loadedSnapshot(viewModel).enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(before)
        viewModel.onCreditInputChange("0.25")
        viewModel.submitCreditDialog()
        composeRule.waitForIdle()

        val after = loadedSnapshot(viewModel).enrolledCourses.first { it.courseCode == "C002" }
        // 来源事实（学分、方案归属、状态、参考说明）逐字段保持不变
        assertEquals(before.courseCode, after.courseCode)
        assertEquals(before.courseName, after.courseName)
        assertEquals(before.status, after.status)
        assertEquals(before.inPlan, after.inPlan)
        assertEquals(before.confirmationHint, after.confirmationHint)
        assertNull(after.creditsText)
        // 本地确认只进覆盖表：官方“待确认”来源事实不被改写，但不再需要用户重复裁决
        assertEquals("0.25", loadedSnapshot(viewModel).localOverrides.getValue("C002").creditsText)
        assertTrue(loadedSnapshot(viewModel).pendingManualCourses.isEmpty())
    }
}

private fun SemanticsNodeInteraction.assertEditableEmpty(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode("read EditableText")
    assertEquals("", node.config.getOrNull(SemanticsProperties.EditableText)?.toString())
    return this
}

private fun SemanticsNodeInteraction.assertHasText(expected: String): SemanticsNodeInteraction =
    assert(hasText(expected, substring = true))
