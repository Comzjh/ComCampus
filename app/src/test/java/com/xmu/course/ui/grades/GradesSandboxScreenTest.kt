package com.xmu.course.ui.grades

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
 * 学业模拟页 UI 测试（合成数据）。
 * 锁定决策：旧「导入已修课程」入口退出主流程；无数据时引导刷新；模拟只在本机推演。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradesSandboxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("academic_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `无学业缓存时显示空态引导且旧导入入口不存在`() {
        setContent(GradesSandboxViewModel())

        composeRule.onNodeWithText("学业模拟").assertIsDisplayed()
        composeRule.onNodeWithTag("grades_seed_empty").assertExists()
        composeRule.onNodeWithText("手动添加模拟课程").assertExists()
        composeRule.onNodeWithTag("grades_import_academic_records").assertDoesNotExist()
    }

    @Test
    fun `空状态点击刷新触发回调`() {
        var refreshed = false
        composeRule.setContent {
            MaterialTheme {
                GradesSandboxScreen(
                    viewModel = GradesSandboxViewModel(),
                    onRefresh = { refreshed = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("刷新学业数据").performClick()
        composeRule.waitForIdle()

        assertTrue(refreshed)
    }

    @Test
    fun `有缓存时显示基线卡和自定义模拟区`() {
        setContent(seededViewModel())

        composeRule.onNodeWithTag("grades_baseline_card").performScrollTo().assertExists()
        composeRule.onNodeWithTag("grades_custom_section").performScrollTo().assertExists()
        composeRule.onNodeWithText("还没添加未来课程", substring = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag("grades_import_academic_records").assertDoesNotExist()
    }

    @Test
    fun `分区顺序为基线到本学期到预计结果到目标再到自定义`() {
        // POL-010：当前基线 → 本学期 → 预计结果 → 目标 GPA（可选） → 自定义未来课程。
        // UX-03：结果从目标区拆出来，不设目标也能看到预计 GPA。
        setContent(seededViewModel())

        val wanted = setOf(
            "grades_baseline_card",
            "grades_semester_section",
            "grades_result_section",
            "grades_target_section",
            "grades_custom_section",
        )
        assertEquals(
            listOf(
                "grades_baseline_card",
                "grades_semester_section",
                "grades_result_section",
                "grades_target_section",
                "grades_custom_section",
            ),
            documentOrderTags().filter { it in wanted },
        )
        // 主操作唯一：重置模拟只是文字按钮，不与「计算模拟 GPA」争夺视觉权重。
        composeRule.onNodeWithText("计算模拟 GPA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("重置模拟").performScrollTo().assertIsDisplayed()
    }

    /** 按语义树的文档顺序收集 testTag：避免依赖滚动后的屏幕坐标。 */
    private fun documentOrderTags(): List<String> {
        val tags = mutableListOf<String>()

        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { tags.add(it) }
            node.children.forEach(::walk)
        }

        walk(composeRule.onRoot().fetchSemanticsNode())
        return tags
    }

    @Test
    fun `刻度与参与规则只在页顶说明一次`() {
        // POL-012：滑块区间与「未设置不参与」在页顶讲清楚，课程行不再逐行重复长句。
        setContent(seededViewModel())

        composeRule.onNodeWithTag("grades_scale_note").assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithText("拖动滑块设分数", substring = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `添加课程并用手动基线模拟GPA`() {
        val viewModel = seededViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        setContent(viewModel)

        composeRule.onNodeWithTag("grades_add_course").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grades_course_name_2").performScrollTo().performTextInput("高等数学")
        composeRule.onNodeWithTag("grades_course_credits_2").performScrollTo().performTextInput("3")
        composeRule.onNodeWithTag("grades_course_grade_2").performScrollTo().performTextInput("A")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grades_simulate").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("grades_result").performScrollTo().assertExists()
        composeRule.onNodeWithText("预计 GPA：4.00").assertExists()
        composeRule.onNodeWithText("模拟后总学分：3.00").assertExists()
    }

    @Test
    fun `无可用基线时模拟给出可恢复提示`() {
        setContent(seededViewModel())

        composeRule.onNodeWithTag("grades_simulate").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("grades_error").assertExists()
        composeRule.onNodeWithText("现有成绩暂无可用绩点", substring = true).assertExists()
    }

    @Test
    fun `删除自定义课程后回到空列表提示`() {
        val viewModel = seededViewModel()
        setContent(viewModel)

        composeRule.onNodeWithTag("grades_add_course").performScrollTo().performClick()
        composeRule.waitForIdle()
        viewModel.removeCourse(2L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("还没添加未来课程", substring = true).assertExists()
    }

    @Test
    fun `已有学分但无可用绩点时只说明缺口不说没数据`() {
        // BUG-05：学业页有数据、模拟页却说“还没有学业数据”的矛盾不免再出现。
        setContent(seededViewModel())

        composeRule.onNodeWithTag("grades_seed_empty").assertDoesNotExist()
        composeRule.onNodeWithText("还没有学业数据").assertDoesNotExist()
        composeRule.onNodeWithText("已有学分进度，暂无可用于 GPA 计算的成绩。")
            .performScrollTo().assertExists()
    }

    @Test
    fun `不设目标GPA也能实时得到预计GPA`() {
        val viewModel = seededViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        setContent(viewModel)

        composeRule.onNodeWithTag("grades_result_placeholder").performScrollTo().assertExists()
        composeRule.onNodeWithTag("grades_add_course").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grades_course_name_2").performTextInput("高等数学")
        composeRule.onNodeWithTag("grades_course_credits_2").performTextInput("3")
        composeRule.onNodeWithTag("grades_course_grade_2").performTextInput("A")
        composeRule.waitForIdle()

        // 没点击「计算模拟 GPA」、目标 GPA 留空，也已经能看到预计值。
        composeRule.onNodeWithTag("grades_result").performScrollTo().assertExists()
        composeRule.onNodeWithText("预计 GPA：4.00").assertExists()
        composeRule.onNodeWithTag("grades_result_gap").assertDoesNotExist()
        composeRule.onNodeWithTag("grades_result_placeholder").assertDoesNotExist()
    }

    @Test
    fun `输入未填完整时不提前报红`() {
        val viewModel = seededViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        setContent(viewModel)

        composeRule.onNodeWithTag("grades_add_course").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grades_course_name_2").performTextInput("线性代数")
        composeRule.onNodeWithTag("grades_course_grade_2").performTextInput("B+")
        composeRule.waitForIdle()

        // 学分还空着：预期结果留白，但不在打字过程中报错。
        composeRule.onNodeWithTag("grades_error").assertDoesNotExist()
        composeRule.onNodeWithTag("grades_result").assertDoesNotExist()
        composeRule.onNodeWithTag("grades_simulate").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grades_error").assertExists()
    }

    @Test
    fun `模拟不修改本机学业缓存`() {
        val viewModel = seededViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        viewModel.addCourse()
        viewModel.updateCourseName(2L, "大学物理")
        viewModel.updateCourseCredits(2L, "2")
        viewModel.updateCourseGrade(2L, "A")
        setContent(viewModel)
        val planFile = File(tempFolder.root, "plan.json")
        val gradeFile = File(tempFolder.root, "grades.json")
        val planBefore = if (planFile.exists()) planFile.readText() else null
        val gradeBefore = if (gradeFile.exists()) gradeFile.readText() else null

        composeRule.onNodeWithTag("grades_simulate").performScrollTo().performClick()
        composeRule.onNodeWithTag("grades_reset").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(planBefore, if (planFile.exists()) planFile.readText() else null)
        assertEquals(gradeBefore, if (gradeFile.exists()) gradeFile.readText() else null)
    }

    @Test
    fun `页面文案不再出现导入成绩Excel与PDF`() {
        // UX-04/UX-05：学业只保留“刷新‍一条真实来源。
        setContent(seededViewModel())
        listOf("Excel", "PDF", "导入成绩", "导入学业").forEach { banned ->
            composeRule.onNodeWithText(banned, substring = true).assertDoesNotExist()
        }
    }

    private fun setContent(viewModel: GradesSandboxViewModel) {
        composeRule.setContent {
            MaterialTheme { GradesSandboxScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()
    }

    /** 合成最小缓存：一张培养方案快照（1 门在修）+ 空成绩缓存，只走本地文件。 */
    private fun seededViewModel(): GradesSandboxViewModel {
        val planStore = AcademicCompletionStore(File(tempFolder.root, "plan.json"))
        assertTrue(
            planStore.import(
                AcademicCompletionSnapshot(
                    schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                    semesterLabel = "2026-2027-1",
                    plan = PlanSummary("合成培养方案", "160", "40", "2026-09-16 22:09", "3"),
                    enrolledCourses = listOf(
                        SourceCourse(
                            "C001", "合成在修课程", "3", SourceXfStatus.CONFIRMED, true,
                            "合成教师", "CL01", null,
                        ),
                    ),
                    completedCoursesOutsidePlan = emptyList(),
                    localOverrides = emptyMap(),
                ),
            ),
        )
        val gradeStore = JwGradeStore(File(tempFolder.root, "grades.json"))
        val policyStore = AcademicGpaPolicyStore(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
        )
        return GradesSandboxViewModel(
            academicStore = planStore,
            gradeStore = gradeStore,
            policyStore = policyStore,
        )
    }

    /** 合成：培养方案含 1 门在修（有学分）+ 成绩缓存含 1 门历史结课 → 基线可算、本学期可播种。 */
    private fun seededComputedViewModel(): GradesSandboxViewModel {
        val planStore = AcademicCompletionStore(File(tempFolder.root, "plan_seed.json"))
        assertTrue(
            planStore.import(
                AcademicCompletionSnapshot(
                    schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                    semesterLabel = "2026-2027-1",
                    plan = PlanSummary("合成培养方案", "160", "40", "2026-09-16 22:09", "3"),
                    enrolledCourses = listOf(
                        SourceCourse("C200", "合成在修甲", "2", SourceXfStatus.CONFIRMED, true, "合成教师", "CL200", null),
                    ),
                    completedCoursesOutsidePlan = emptyList(),
                    localOverrides = emptyMap(),
                ),
            ),
        )
        val gradeStore = JwGradeStore(File(tempFolder.root, "grade_seed.json"))
        assertTrue(gradeStore.replace(syntheticGradeSnapshot()))
        val policyStore = AcademicGpaPolicyStore(ApplicationProvider.getApplicationContext())
        return GradesSandboxViewModel(
            academicStore = planStore,
            gradeStore = gradeStore,
            policyStore = policyStore,
        )
    }

    /** 合成：只有成绩缓存、无培养方案快照（复现设备现场：GPA 可算但缺学分提示）。 */
    private fun gradeOnlyViewModel(): GradesSandboxViewModel {
        val planStore = AcademicCompletionStore(File(tempFolder.root, "plan_none.json"))
        val gradeStore = JwGradeStore(File(tempFolder.root, "grade_none.json"))
        assertTrue(gradeStore.replace(syntheticGradeSnapshot()))
        val policyStore = AcademicGpaPolicyStore(ApplicationProvider.getApplicationContext())
        return GradesSandboxViewModel(
            academicStore = planStore,
            gradeStore = gradeStore,
            policyStore = policyStore,
        )
    }

    private fun syntheticGradeSnapshot(): JwGradeSnapshot = JwGradeSnapshot(
        refreshedAtEpochMillis = 1_770_000_000_000L,
        providerId = "xmu.jw",
        sourceCapability = "cjcx.xscjcx",
        totalCreditsText = "3",
        entries = listOf(
            JwGradeEntry(
                "R1", "2024-2025-1", "2024学年", "C100", "合成历史甲",
                "3", "87", "3.7", "必修", null, null, null,
            ),
        ),
    )

    @Test
    fun `本学期在修课程自动播种为可编辑滑块且无需目标即实时得到预计GPA`() {
        val viewModel = seededComputedViewModel()
        val seeded = viewModel.uiState.value.courses.filter { it.seeded }
        assertEquals(1, seeded.size)
        val row = seeded.single()
        assertEquals("C200", row.key.removePrefix(SEEDED_COURSE_PREFIX))
        assertEquals("2", row.credits)
        // 基线学分与展示 GPA 同源（C100 的 3 学分），本学期在修 C200 学分不并入基线。
        assertEquals("3", viewModel.uiState.value.seed.baseline?.earnedCreditsText)
        assertTrue(viewModel.uiState.value.canSimulate)

        // 不设目标 GPA：仅拖动在修课程滑块即可实时得到预计 GPA。
        viewModel.updateCourseScore(row.id, 100)
        val high = viewModel.uiState.value.result
        assertNotNull(high)
        viewModel.updateCourseScore(row.id, 60)
        val low = viewModel.uiState.value.result
        assertNotNull(low)
        assertTrue((low?.gpa ?: 0.0) < (high?.gpa ?: 0.0))
    }

    @Test
    fun `仅成绩缓存无培养方案时基线显示已修学分并给出本学期空态`() {
        setContent(gradeOnlyViewModel())
        // Fix A：基线不再“暂无”，显示与 GPA 同源的学分合计。
        composeRule.onNodeWithText("已修学分：3").assertIsDisplayed()
        // 不再出现误导的“已有成绩数据，仍缺少学分信息”。
        composeRule.onNodeWithText("已有成绩数据，仍缺少计算所需的学分信息。").assertDoesNotExist()
        // Fix B / CASE B：明确提示本学期暂无可模拟课程，而非笼统“没数据”。
        composeRule.onNodeWithTag("grades_semester_empty").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("grades_semester_empty_text").assertExists()
    }

    /** 受控 xywccx fixture：3 门在修（学分 2/1/3）+ cjcx 历史成绩，走生产 seed 链路。 */
    private class SeededFixture(
        val viewModel: GradesSandboxViewModel,
        val planFile: File,
        val gradeFile: File,
    )

    private fun threeCourseXywccxFixture(): SeededFixture {
        val planFile = File(tempFolder.root, "plan_xyz.json")
        val gradeFile = File(tempFolder.root, "grade_xyz.json")
        val planStore = AcademicCompletionStore(planFile)
        assertTrue(
            planStore.import(
                AcademicCompletionSnapshot(
                    schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                    semesterLabel = "合成验收学期",
                    plan = PlanSummary("合成培养方案", "160", "999", "2026-09-23", "6"),
                    enrolledCourses = listOf(
                        SourceCourse("SYN001", "合成在修甲", "2", SourceXfStatus.CONFIRMED, true, "合成教师", "X1", null),
                        SourceCourse("SYN002", "合成在修乙", "1", SourceXfStatus.CONFIRMED, true, "合成教师", "X2", null),
                        SourceCourse("SYN003", "合成在修丙", "3", SourceXfStatus.CONFIRMED, true, "合成教师", "X3", null),
                    ),
                    completedCoursesOutsidePlan = emptyList(),
                    localOverrides = emptyMap(),
                ),
            ),
        )
        val gradeStore = JwGradeStore(gradeFile)
        assertTrue(gradeStore.replace(syntheticGradeSnapshot()))
        return SeededFixture(
            GradesSandboxViewModel(
                academicStore = planStore,
                gradeStore = gradeStore,
                policyStore = AcademicGpaPolicyStore(ApplicationProvider.getApplicationContext()),
            ),
            planFile,
            gradeFile,
        )
    }

    @Test
    fun `xywccx三门在修自动播种三行含学分与滑块无需手工录入且不回写缓存`() {
        val fixture = threeCourseXywccxFixture()
        val planBefore = fixture.planFile.readText()
        val gradeBefore = fixture.gradeFile.readText()
        val viewModel = fixture.viewModel
        setContent(viewModel)

        // 1+5：三门在修自动出现，顺序与来源一致；无需手工添加未来课程。
        val seeded = viewModel.uiState.value.courses.filter { it.seeded }
        assertEquals(
            listOf("SYN001", "SYN002", "SYN003"),
            seeded.map { it.key.removePrefix(SEEDED_COURSE_PREFIX) },
        )
        assertEquals(listOf("2", "1", "3"), seeded.map { it.credits })
        composeRule.onNodeWithTag("grades_semester_section").performScrollTo().assertExists()
        composeRule.onNodeWithText("学分 2").performScrollTo().assertExists()
        composeRule.onNodeWithText("学分 1").performScrollTo().assertExists()
        composeRule.onNodeWithText("学分 3").performScrollTo().assertExists()
        composeRule.onNodeWithTag("grades_empty_courses").assertExists()

        // 3：尚无真实成绩的在修课程照样出现（初始不参与，等待用户设分）。
        assertTrue(seeded.none { it.participates })

        // 6+7：cjcx 历史成绩只进基线；本学期学分不并入已修学分（999 是方案原文，故意不等于分母）。
        assertEquals("3", viewModel.uiState.value.seed.baseline?.earnedCreditsText)
        assertTrue(viewModel.uiState.value.courses.none { it.key.contains("C100") })

        // 4：目标 GPA 留空也能立刻得到预计 GPA，且随滑块变化。
        assertEquals("", viewModel.uiState.value.targetGpa)
        viewModel.updateCourseScore(seeded[0].id, 90)
        val first = viewModel.uiState.value.result
        assertNotNull(first)
        viewModel.updateCourseScore(seeded[0].id, 61)
        val second = viewModel.uiState.value.result
        assertNotNull(second)
        assertTrue((second?.gpa ?: 0.0) < (first?.gpa ?: 0.0))
        composeRule.onNodeWithTag("grades_result").performScrollTo().assertExists()

        // 8：模拟只读，两个本机仓库文件逐字节未变。
        assertEquals(planBefore, fixture.planFile.readText())
        assertEquals(gradeBefore, fixture.gradeFile.readText())
    }
}
