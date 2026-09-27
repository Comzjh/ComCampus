package com.xmu.course.ui.academic

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import com.xmu.course.ui.scrollUntilComposed
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
 * GPA 页 UI 测试（合成数据）。
 *
 * 锁定：方案外课程必须由用户裁决；未裁决不出数；
 * 选择只改变本地派生值；页面绝不自称官方 GPA。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicGpaScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var policyStore: AcademicGpaPolicyStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("academic_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        policyStore = AcademicGpaPolicyStore(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun planStore(): AcademicCompletionStore {
        val store = AcademicCompletionStore(File(tempFolder.root, "plan.json"))
        assertTrue(
            store.import(
                AcademicCompletionSnapshot(
                    schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                    semesterLabel = "2026-2027-1",
                    plan = PlanSummary("合成培养方案", "160", "40", "2026-09-16 22:09", "3"),
                    enrolledCourses = listOf(
                        SourceCourse("X001", "方案外课程", null, SourceXfStatus.NEEDS_MANUAL, false, "", "", null),
                    ),
                    completedCoursesOutsidePlan = emptyList(),
                    localOverrides = emptyMap(),
                ),
            ),
        )
        return store
    }

    private fun gradeStore(): JwGradeStore {
        val store = JwGradeStore(File(tempFolder.root, "grades.json"))
        assertTrue(
            store.replace(
                JwGradeSnapshot(
                    refreshedAtEpochMillis = 1_770_000_000_000L,
                    providerId = "xmu.jw",
                    sourceCapability = "cjcx.xscjcx",
                    totalCreditsText = "6",
                    entries = listOf(
                        JwGradeEntry(
                            "R1", "2025-2026-1", "2025-2026学年第一学期", "C100", "计划内课程",
                            "3", "87", "3.7", "必修", null, null, null,
                        ),
                        JwGradeEntry(
                            "R2", "2025-2026-1", "2025-2026学年第一学期", "X001", "方案外课程",
                            "3", "70", "2.0", "选修", null, null, null,
                        ),
                    ),
                ),
            ),
        )
        return store
    }

    private fun show(): AcademicGpaViewModel {
        val viewModel = AcademicGpaViewModel(planStore(), gradeStore(), policyStore)
        composeRule.setContent {
            MaterialTheme {
                AcademicGpaScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenSandbox = { sandboxClicks++ },
                )
            }
        }
        return viewModel
    }

    private var sandboxClicks = 0

    @Test
    fun unconfirmedPolicyShowsPendingWithoutNumber() {
        show()
        composeRule.onNodeWithTag("academic_gpa_result_card").assertIsDisplayed()
        composeRule.onNodeWithTag("academic_gpa_pending").assertIsDisplayed()
        composeRule.onNodeWithText("待确认，暂不出数").assertIsDisplayed()
        composeRule.onNodeWithTag("academic_gpa_computed").assertDoesNotExist()
    }

    @Test
    fun candidatesAreListedForUserJudgement() {
        show()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_candidates")).assertExists()
        composeRule.scrollUntilComposed(hasText("方案外课程（1 门）")).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasText("方案外课程")).assertIsDisplayed()
    }

    @Test
    fun policyChoiceIsPersistedAndComputesImmediately() {
        show()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_policy_include")).performClick()
        assertEquals(OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN, policyStore.policy.value)
        composeRule.waitForIdle()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_computed")).assertExists()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_method")).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_metric_courses")).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_metric_credits")).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasText("2 门")).assertIsDisplayed()
    }

    @Test
    fun tappingPolicyLabelUpdatesTheAccessibleRadioSelection() {
        show()

        composeRule.scrollUntilComposed(hasText("计入本地 GPA")).performClick()
        composeRule.waitForIdle()

        assertEquals(OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN, policyStore.policy.value)
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_policy_include")).assertIsSelected()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_policy_exclude")).assertIsNotSelected()
    }

    @Test
    fun excludePolicyRemovesOnlyDerivedContribution() {
        show()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_policy_exclude")).performClick()
        composeRule.waitForIdle()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_computed")).assertExists()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_adjustments")).assertExists()
        composeRule.scrollUntilComposed(hasText("按你的选择不计入 1 门", substring = true)).assertIsDisplayed()
        // 官方记录仍在历史成绩中原样保留：本页不提供任何删除/改写官方数据的入口
        composeRule.onNodeWithText("清除官方成绩").assertDoesNotExist()
    }

    @Test
    fun wordingNeverClaimsOfficialGpa() {
        show()
        composeRule.onNodeWithText("本地计算 GPA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("官方 GPA").assertDoesNotExist()
        composeRule.onNodeWithText(
"学校培养方案归属可能存在延迟或异常。你的选择只影响本机计算，不会修改教务数据，也不会写回学校。",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun whatIfSimulationIsSecondaryEntry() {
        show()
        composeRule.scrollUntilComposed(hasTestTag("academic_gpa_open_sandbox")).performClick()
        assertEquals(1, sandboxClicks)
        composeRule.waitForIdle()
        // AppNavigationRow 可点击会合并子节点，标题按片段匹配
        composeRule.scrollUntilComposed(hasText("成绩模拟（What-if）", substring = true)).assertIsDisplayed()
    }
}
