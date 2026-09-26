package com.xmu.course.ui.academic

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.ui.scrollUntilComposed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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
 * 历史成绩 UI 测试（合成数据）。
 *
 * 锁定：按学期分组、官方成绩/学分/绩点文本原样呈现、
 * 方案外标记来自培养方案归属，不同口径只解释不合并。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun planStore(withOutside: Boolean = true): AcademicCompletionStore {
        val store = AcademicCompletionStore(File(tempFolder.root, "plan.json"))
        assertTrue(
            store.import(
                AcademicCompletionSnapshot(
                    schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                    semesterLabel = "2026-2027-1",
                    plan = PlanSummary("合成培养方案", "160", "40", "2026-09-16 22:09", "3"),
                    enrolledCourses = if (withOutside) {
                        listOf(
                            SourceCourse("X001", "合成课程乙", null, SourceXfStatus.NEEDS_MANUAL, false, "", "", null),
                        )
                    } else {
                        emptyList()
                    },
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
                    totalCreditsText = "55",
                    entries = listOf(
                        JwGradeEntry(
                            "R1", "2025-2026-1", "2025-2026学年第一学期", "C100", "历史课程甲",
                            "3", "87", "3.7", "必修", null, null, null,
                        ),
                        JwGradeEntry(
                            "R2", "2025-2026-1", "2025-2026学年第一学期", "C101", "历史课程丙",
                            "2", "合格", "N/A", null, null, null, null,
                        ),
                        JwGradeEntry(
                            "R3", "2025-2026-2", "2025-2026学年第二学期", "X001", "方案外课程",
                            "1.5", "92", "4.0", "选修", null, null, null,
                        ),
                    ),
                ),
            ),
        )
        return store
    }

    private fun show(plan: AcademicCompletionStore = planStore(), grades: JwGradeStore = gradeStore()) {
        composeRule.setContent {
            MaterialTheme {
                AcademicHistoryScreen(
                    viewModel = AcademicHistoryViewModel(plan, grades),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun emptyStatePointsToAcademicRefreshInPlainWords() {
        show(
            plan = AcademicCompletionStore(File(tempFolder.root, "no-plan.json")),
            grades = JwGradeStore(File(tempFolder.root, "no-grades.json")),
        )
        composeRule.onNodeWithTag("academic_history_empty").assertIsDisplayed()
        composeRule.onNodeWithText("历史成绩尚未刷新").assertIsDisplayed()
"回到「学业」页点击刷新，即可补齐各学期成绩。"
    }

    @Test
    fun semestersGroupCoursesWithOfficialText() {
        show()
        composeRule.onNodeWithTag("academic_history_semester_2025-2026-1").performScrollTo().assertExists()
        composeRule.onNodeWithText("2 门 · 5 学分").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("历史课程甲").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("必修 · 3 学分").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("87").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("绩点 3.7").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun passFailCourseKeepsGradeTextAndHidesFakePoint() {
        show()
        composeRule.onNodeWithText("合格").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2 学分").performScrollTo().assertIsDisplayed()
        // N/A 不得被渲染成"绩点 N/A"
        composeRule.onNodeWithText("绩点 N/A").assertDoesNotExist()
    }

    @Test
    fun outsidePlanMarkComesFromPlanAttribution() {
        show()
        composeRule.scrollUntilComposed(hasTestTag("academic_history_semester_2025-2026-2")).assertExists()
        composeRule.scrollUntilComposed(hasText("含方案外课程")).assertIsDisplayed()
        composeRule.scrollUntilComposed(hasText("方案外")).assertIsDisplayed()
    }

    @Test
    fun outsidePlanMarkAbsentWithoutPlanAttribution() {
        // 无培养方案归属时不得凭空标记：先筛到该学期，确保这一组行确实被渲染过
        show(plan = planStore(withOutside = false))
        composeRule.onNodeWithTag("academic_history_filter_2025-2026-2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("academic_history_semester_2025-2026-2").performScrollTo().assertExists()
        // 该学期唯一一条官方记录仍然原样显示，只是没有任何方案外标记
        composeRule.onNodeWithText("方案外课程").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("含方案外课程").assertDoesNotExist()
        // chip 文本恰为“方案外”，与课程名“方案外课程”是不同节点：必须精确匹配才不会被课程名误命中
        composeRule.onNodeWithText("方案外").assertDoesNotExist()
    }

    @Test
    fun semesterFilterNarrowsVisibleGroups() {
        show()
        composeRule.onNodeWithTag("academic_history_filter_2025-2026-2").performClick()
        composeRule.onNodeWithTag("academic_history_semester_2025-2026-2").performScrollTo().assertExists()
        composeRule.onNodeWithTag("academic_history_semester_2025-2026-1").assertDoesNotExist()
        composeRule.onNodeWithTag("academic_history_filter_all").performClick()
        composeRule.onNodeWithTag("academic_history_semester_2025-2026-1").performScrollTo().assertExists()
    }

    @Test
    fun sourceDiscrepancyIsExplainedInsteadOfMerged() {
        show()
        composeRule.scrollUntilComposed(hasTestTag("academic_history_footnote")).assertExists()
        composeRule.onNodeWithText(
            "不同教务页面的统计口径可能不同：已修学分以培养方案为准，本页为成绩记录明细。",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun noParserOrProtocolKeysLeakIntoUi() {
        show()
        listOf("cjcx", "xscjcx", "KCDM", "XFJD", "querySetting", "JSESSIONID").forEach { term ->
            composeRule.onNodeWithText(term, substring = true).assertDoesNotExist()
        }
    }
}