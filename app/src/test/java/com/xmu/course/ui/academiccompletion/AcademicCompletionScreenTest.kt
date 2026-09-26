package com.xmu.course.ui.academiccompletion

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.LocalAcademicSnapshotParser
import com.xmu.course.data.academiccompletion.SnapshotImportResult
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
 * 培养方案详情页 UI 测试（合成数据）。
 *
 * 该页只读本机快照：在修课程与学分补录已迁到「本学期」，文件导入通道已退出正式产品。
 * 断言重点：官方方案数字 / 来源确认 / 本地补充三态分别呈现，GPA 口径留给用户裁决。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicCompletionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = UnconfinedTestDispatcher()

    private var semesterOpens = 0

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
          "faw_completed_courses": [
            {"KCH":"X001","KCMC":"合成课外的课","XF":2,"XNXQDM":"2025-2026-2","CJ":88}
          ],
          "totals": {"in_plan_xf_sum": 3}
        }
    """.trimIndent()

    /** 全部课程学分已确认：用于验证「无待确认课程」时的常驻入口与教程锚点。 */
    private val noPendingJson = validJson.replace(
        "\"KCMC\":\"合成课程乙\",\"XF\":null,\"in_plan\":false,\"xf_status\":\"需人工确认\"",
        "\"KCMC\":\"合成课程乙\",\"XF\":2,\"in_plan\":false,\"xf_status\":\"confirmed\"",
    )

    private fun newStore(imported: Boolean = true, json: String = validJson): AcademicCompletionStore {
        val store = AcademicCompletionStore(File(tempFolder.root, "snapshot.json"))
        if (imported) {
            val parsed = LocalAcademicSnapshotParser.parse(json)
            assertTrue(parsed is SnapshotImportResult.Success)
            store.import((parsed as SnapshotImportResult.Success).snapshot)
        }
        return store
    }

    private fun showContent(imported: Boolean = true, json: String = validJson): AcademicCompletionViewModel {
        val viewModel = AcademicCompletionViewModel(
            newStore(imported, json),
            UnconfinedTestDispatcher(mainDispatcher.scheduler),
        )
        composeRule.setContent {
            AcademicCompletionScreen(
                viewModel = viewModel,
                onBack = {},
                onOpenSemester = { semesterOpens++ },
            )
        }
        return viewModel
    }

    private fun loadedSnapshot(
        viewModel: AcademicCompletionViewModel,
    ): AcademicCompletionSnapshot =
        (viewModel.uiState.value.storeState as AcademicCompletionStore.State.Loaded).snapshot
    @Test
    fun emptyStatePointsToAcademicRefreshInPlainWords() {
        showContent(imported = false)
        composeRule.onNodeWithTag("academic_completion_empty").assertIsDisplayed()
        composeRule.onNodeWithText("本机还没有培养方案数据").assertIsDisplayed()
        composeRule.onNodeWithText(
            "回到「学业」页点击刷新，即可从教务获取培养方案进度。",
        ).assertIsDisplayed()
        // 过渡期的文件导入通道不得再出现在正式页面上
        composeRule.onNodeWithText("选择快照文件").assertDoesNotExist()
    }

    @Test
    fun semesterEntryStaysAnchoredWithoutPendingCourses() {
        // POL-004 / TUT-006：没有方案外待确认课程时，本学期入口必须常驻，教程锚点不失效。
        showContent(json = noPendingJson)

        composeRule.onNodeWithTag("academic_completion_pending_count").assertDoesNotExist()
        composeRule.onNodeWithTag("academic_completion_open_semester")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("查看本学期在修课程").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("去本学期确认").assertDoesNotExist()
    }

    @Test
    fun planOverviewKeepsOfficialFiguresApartFromLocalTotals() {
        showContent()
        composeRule.onNodeWithTag("academic_completion_overview").assertIsDisplayed()
        composeRule.onNodeWithText("合成方案 · 2026-2027-1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已获学分 40 / 160").assertIsDisplayed()
        composeRule.onNodeWithTag("academic_completion_source_total")
            .assertHasText("来源已确认 · 本学期已选 3 学分")
        composeRule.onNodeWithTag("academic_completion_snapshot_time")
            .assertHasText("学校页面统计时间：2026-09-16 22:09")
        // 没有任何本地确认时，不得凭空出现“本地补充/合计”行
        composeRule.onNodeWithTag("academic_completion_local_total").assertDoesNotExist()
        composeRule.onNodeWithTag("academic_completion_combined_total").assertDoesNotExist()
    }

    @Test
    fun pendingManualCreditsAreCountedAndLeadToSemesterPage() {
        showContent()
        // AppStatusChip 是容器，文本位于子节点：按片段匹配
        composeRule.onNodeWithText("1 门课程学分待确认", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("academic_completion_open_semester").performScrollTo().performClick()
        assertEquals(1, semesterOpens)
    }

    @Test
    fun outsidePlanCompletedCoursesLeaveGpaDecisionToUser() {
        showContent()
        // 第二张卡在 Robolectric 小视口下尚未组合：先滚到它被真正渲染
        composeRule.scrollUntilComposed(hasText("方案外已结课（1）")).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasText("合成课外的课")).assertIsDisplayed()
        composeRule.onNodeWithTag("academic_completion_completed_X001").assertHasText("2 学分 · 成绩 88")
        composeRule.onNodeWithText("由你在「GPA 计算」中确认", substring = true).assertIsDisplayed()
        // 页面绝不替用户宣布“不计入 GPA”
        composeRule.onNodeWithText("不计入 GPA", substring = true).assertDoesNotExist()
    }

    @Test
    fun localSupplementStaysApartFromOfficialSourceTotal() {
        val viewModel = showContent()
        val pendingCourse = loadedSnapshot(viewModel).enrolledCourses.first { it.courseCode == "C002" }
        viewModel.openCreditDialog(pendingCourse)
        viewModel.onCreditInputChange("2")
        viewModel.submitCreditDialog()
        composeRule.waitForIdle()

        val saved = loadedSnapshot(viewModel)
        // 本地确认只写本机覆盖值，官方“来源暂无学分”事实保持不变
        assertNull(saved.enrolledCourses.first { it.courseCode == "C002" }.creditsText)
        assertEquals("2", saved.localOverrides.getValue("C002").creditsText)

        composeRule.onNodeWithTag("academic_completion_source_total").assertHasText("本学期已选 3 学分")
        composeRule.onNodeWithTag("academic_completion_local_total").assertHasText("本地补充 2 学分")
        composeRule.onNodeWithTag("academic_completion_combined_total").assertHasText("含本地确认合计 5 学分")
        composeRule.onNodeWithText("不属于教务来源总计", substring = true).assertIsDisplayed()
    }

    @Test
    fun clearRequiresConfirmationAndCancelKeepsSnapshot() {
        val viewModel = showContent()
        composeRule.onNodeWithTag("academic_completion_clear").performScrollTo().performClick()
        composeRule.onNodeWithTag("academic_completion_clear_cancel").performClick()
        assertTrue(viewModel.uiState.value.storeState is AcademicCompletionStore.State.Loaded)

        composeRule.onNodeWithTag("academic_completion_clear").performClick()
        composeRule.onNodeWithTag("academic_completion_clear_confirm").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.storeState is AcademicCompletionStore.State.Empty)
        composeRule.onNodeWithTag("academic_completion_empty").assertIsDisplayed()
    }

    @Test
    fun storageFailureShowsRecoverableMessageWithoutImportAffordance() {
        val file = File(tempFolder.root, "corrupt.json")
        file.writeText("garbage{[")
        val viewModel = AcademicCompletionViewModel(
            AcademicCompletionStore(file),
            UnconfinedTestDispatcher(mainDispatcher.scheduler),
        )
        composeRule.setContent {
            AcademicCompletionScreen(viewModel = viewModel, onBack = {})
        }
        composeRule.onNodeWithTag("academic_completion_storage_failure").assertIsDisplayed()
        composeRule.onNodeWithText("可在「数据管理」中删除后重新刷新", substring = true).assertIsDisplayed()
        // 损坏状态下也不给静默覆盖或文件导入通道
        composeRule.onNodeWithText("选择快照文件").assertDoesNotExist()
    }
}

private fun SemanticsNodeInteraction.assertHasText(expected: String): SemanticsNodeInteraction =
    assert(hasText(expected, substring = true))
