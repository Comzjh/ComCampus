package com.xmu.course.ui.academic

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.CompletedCourseOutsidePlan
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
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
 * 新「学业」首页 UI 测试（合成数据）。
 *
 * 锁定产品决策：首页第一视觉是学业状态而不是工具箱；刷新只在用户点击时发生；
 * 方案外未确认时 GPA 绝不出数；界面不出现协议/凭据术语。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicHomeScreenTest {

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

    private fun planStore(withSnapshot: Boolean = true): AcademicCompletionStore {
        val store = AcademicCompletionStore(File(tempFolder.root, "plan.json"))
        if (withSnapshot) {
            assertTrue(
                store.import(
                    AcademicCompletionSnapshot(
                        schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                        semesterLabel = "2026-2027-1",
                        plan = PlanSummary("合成培养方案", "160", "40", "2026-09-16 22:09", "3"),
                        enrolledCourses = listOf(
                            SourceCourse(
                                "C001", "合成课程甲", "3", SourceXfStatus.CONFIRMED, true,
                                "合成教师", "CL01", null,
                            ),
                            SourceCourse(
                                "X001", "合成课程乙", null, SourceXfStatus.NEEDS_MANUAL, false,
                                "合成教师", "CL02", null,
                            ),
                        ),
                        completedCoursesOutsidePlan = listOf(
                            CompletedCourseOutsidePlan("Y001", "方案外结课", "2", "2024-2025-2", "80"),
                        ),
                        localOverrides = emptyMap(),
                        fetchedAtEpochMillis = 1_780_000_000_000L,
                    ),
                ),
            )
        }
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
                            "R1", "2025-2026-1", "2025-2026学年第一学期", "C100", "历史课程甲",
                            "3", "87", "3.7", "必修", null, null, null,
                        ),
                        JwGradeEntry(
                            "R2", "2025-2026-2", "2025-2026学年第二学期", "X001", "历史课程乙",
                            "3", "92", "4.0", "选修", null, null, null,
                        ),
                    ),
                ),
            ),
        )
        return store
    }

    private fun emptyGradeStore(): JwGradeStore = JwGradeStore(File(tempFolder.root, "empty-grades.json"))

    private fun loadedEmptyGradeStore(): JwGradeStore {
        val store = JwGradeStore(File(tempFolder.root, "loaded-empty-grades.json"))
        assertTrue(
            store.replace(
                JwGradeSnapshot(
                    refreshedAtEpochMillis = 1_770_000_000_000L,
                    providerId = "xmu.jw",
                    sourceCapability = "cjcx.xscjcx",
                    totalCreditsText = "0",
                    entries = emptyList(),
                ),
            ),
        )
        return store
    }

    private fun brokenGradeStore(): JwGradeStore {
        val file = File(tempFolder.root, "broken.json").also { it.parentFile?.mkdirs() }
        file.writeText("{not-json")
        return JwGradeStore(file)
    }

    private fun showScreen(
        plan: AcademicCompletionStore = planStore(),
        grades: JwGradeStore = emptyGradeStore(),
        onRefresh: () -> Unit = {},
        opens: MutableMap<String, Int> = mutableMapOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                AcademicHomeScreen(
                    viewModel = AcademicHomeViewModel(plan, grades, policyStore),
                    onRefresh = onRefresh,
                    onOpenSemester = { opens["semester"] = (opens["semester"] ?: 0) + 1 },
                    onOpenPlan = { opens["plan"] = (opens["plan"] ?: 0) + 1 },
                    onOpenHistory = { opens["history"] = (opens["history"] ?: 0) + 1 },
                    onOpenGpa = { opens["gpa"] = (opens["gpa"] ?: 0) + 1 },
                    onOpenSimulation = { opens["simulation"] = (opens["simulation"] ?: 0) + 1 },
                    onOpenDataSources = { opens["sources"] = (opens["sources"] ?: 0) + 1 },
                )
            }
        }
    }

    private fun assertNoProtocolTerms() {
        listOf("cjcx", "xywccx", "gsapp", "Cookie", "JSESSIONID", "priming", "querySetting", "KCDM")
            .forEach { term ->
                composeRule.onNodeWithText(term, substring = true).assertDoesNotExist()
            }
    }

    @Test
    fun firstRunShowsPlainEmptyStateWithoutToolbox() {
        showScreen(plan = planStore(withSnapshot = false))
        composeRule.onNodeWithText("学业").assertIsDisplayed()
        composeRule.onNodeWithTag("academic_home_empty").assertIsDisplayed()
        composeRule.onNodeWithText(
            "手动刷新成绩与培养方案，数据只保存在本机。",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("academic_gpa_card").assertDoesNotExist()
        composeRule.onNodeWithTag("academic_open_simulation").assertIsDisplayed()
        // 旧「成绩工具箱」四宫格不得再出现在正式产品里
        composeRule.onNodeWithText("成绩记录").assertDoesNotExist()
        composeRule.onNodeWithText("GPA 模拟器").assertDoesNotExist()
        composeRule.onNodeWithText("手动录入成绩").assertDoesNotExist()
        composeRule.onNodeWithText("导入成绩表").assertDoesNotExist()
    }

    @Test
    fun loadedEmptyGradesExplainTheResultAndKeepRefreshManual() {
        var refreshes = 0
        showScreen(
            plan = planStore(withSnapshot = false),
            grades = loadedEmptyGradeStore(),
            onRefresh = { refreshes++ },
        )

        composeRule.onNodeWithTag("academic_home_empty").assertDoesNotExist()
        composeRule.onNodeWithTag("academic_home_no_grades").assertIsDisplayed()
        composeRule.onNodeWithText("暂无可显示的成绩").assertIsDisplayed()
        composeRule.onNodeWithText(
            "最近一次成绩查询没有返回成绩记录。有新成绩时可以手动刷新；数据只保存在这台手机。",
        ).assertIsDisplayed()
        assertEquals(0, refreshes)
        composeRule.onNodeWithText("刷新学业数据").performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun refreshHappensOnlyOnClick() {
        var refreshes = 0
        showScreen(plan = planStore(withSnapshot = false), onRefresh = { refreshes++ })
        // 组合本身不触发任何刷新：MANUAL_ONLY，无自动/后台访问官方数据
        assertEquals(0, refreshes)
        composeRule.onNodeWithText("刷新学业数据").performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun dataStateHidesRefreshHero() {
        var refreshes = 0
        showScreen(onRefresh = { refreshes++ })
        // 规格要求：已有数据时首页不再出现大按钮刷新入口，刷新收敛到「数据与来源」
        composeRule.onNodeWithTag("academic_refresh_button").assertDoesNotExist()
        assertEquals(0, refreshes)
    }

    @Test
    fun realDataHomeShowsProgressPlanAndHistory() {
        showScreen(plan = planStore(), grades = gradeStore())
        composeRule.onNodeWithTag("academic_overview_card").assertIsDisplayed()
        composeRule.onNodeWithText("合成培养方案").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("本机获取时间", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("academic_semester_card").performScrollTo().assertExists()
        composeRule.onNodeWithTag("academic_history_card").performScrollTo().assertExists()
        composeRule.onNodeWithText("已修学分").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp", fontScale = 1.5f)
    fun overviewCountsKeepFullWidthWhenStatusBadgesArePresentAtLargeFontScale() {
        showScreen(plan = planStore(), grades = gradeStore())

        val enrolledBounds = composeRule.onNodeWithTag("academic_enrolled_count")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("在修说明保持完整行宽", enrolledBounds.right - enrolledBounds.left >= 250.dp)

        val chipsBounds = composeRule.onNodeWithTag("academic_overview_status_chips")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("状态徽标排在说明下方", chipsBounds.top >= enrolledBounds.bottom)
        composeRule.onNodeWithText("方案外 1 门").assertIsDisplayed()
        composeRule.onNodeWithText("学分待确认 1").assertIsDisplayed()
    }

    @Test
    fun unconfirmedOutsidePlanShowsNoGpaNumber() {
        showScreen(plan = planStore(), grades = gradeStore())
        composeRule.onNodeWithTag("academic_gpa_card").performScrollTo().assertExists()
        composeRule.onNodeWithTag("academic_gpa_needs_confirmation").performScrollTo().assertExists()
        composeRule.onNodeWithTag("academic_gpa_value").assertDoesNotExist()
    }

    @Test
    fun confirmedPolicyAllowsLocalGpaLabelledAsDerived() {
        policyStore.setPolicy(OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN)
        showScreen(plan = planStore(), grades = gradeStore())
        composeRule.onNodeWithTag("academic_gpa_value").performScrollTo().assertExists()
        composeRule.onNodeWithText("本地计算 GPA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("academic_gpa_metric_courses").performScrollTo().assertExists()
        composeRule.onNodeWithTag("academic_gpa_metric_credits").assertExists()
    }

    @Test
    fun dataAndSettingsRowsNavigate() {
        val opens = mutableMapOf<String, Int>()
        showScreen(plan = planStore(), grades = gradeStore(), opens = opens)
        listOf(
            "academic_open_plan" to "plan",
            "academic_open_gpa_settings" to "gpa",
            "academic_open_simulation" to "simulation",
            "academic_open_data_sources" to "sources",
            "academic_open_semester" to "semester",
            "academic_open_history" to "history",
        ).forEach { (tag, key) ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
            assertEquals(1, opens[key])
        }
    }

    @Test
    fun corruptCacheIsReportedWithoutFakeEmptiness() {
        showScreen(plan = planStore(), grades = brokenGradeStore())
        composeRule.onNodeWithTag("academic_storage_failure").performScrollTo().assertExists()
        composeRule.onNodeWithTag("academic_home_empty").assertDoesNotExist()
    }

    @Test
    fun emptyStateSecondaryEntryExplainsSourcesInPlainWords() {
        showScreen(plan = planStore(withSnapshot = false))
        composeRule.onNodeWithTag("academic_home_about_sources").performClick()
        composeRule.onNodeWithText("数据来源说明").assertIsDisplayed()
        assertNoProtocolTerms()
    }

    @Test
    fun populatedHomeHidesProtocolAndCredentialTerms() {
        showScreen(plan = planStore(), grades = gradeStore())
        assertNoProtocolTerms()
    }
}
