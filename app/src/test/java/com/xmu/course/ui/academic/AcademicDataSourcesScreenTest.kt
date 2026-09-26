package com.xmu.course.ui.academic

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.jwgrades.JwGradeStore
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp", fontScale = 1.5f)
class AcademicDataSourcesScreenTest {

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

    @Test
    fun longPlanNameWrapsAndRefreshActionGrowsForLargeText() {
        val planName = "2025级化学类本科生培养方案（普通全日制）暨跨学科强化课程方案"
        val academicStore = AcademicCompletionStore(File(tempFolder.root, "plan.json"))
        assertTrue(
            academicStore.import(
                AcademicCompletionSnapshot(
                    schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
                    semesterLabel = "2026-2027-1",
                    plan = PlanSummary(planName, "160", "40", "2026-09-16 22:09", "3"),
                    enrolledCourses = emptyList(),
                    completedCoursesOutsidePlan = emptyList(),
                    localOverrides = emptyMap(),
                    fetchedAtEpochMillis = 1_780_000_000_000L,
                ),
            ),
        )
        val viewModel = AcademicDataSourcesViewModel(
            academicStore = academicStore,
            gradeStore = JwGradeStore(File(tempFolder.root, "grades.json")),
        )

        composeRule.setContent {
            MaterialTheme {
                AcademicDataSourcesScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onRefresh = {},
                    onOpenDataManagement = {},
                    onOpenGpaSettings = {},
                    onOpenAcademicReport = {},
                    onOpenCertificate = {},
                    onOpenCampusService = {},
                )
            }
        }

        val planRow = composeRule.onNodeWithTag("datasources_plan_row")
            .performScrollTo()
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val planLabelBounds = composeRule.onNodeWithText("培养方案")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val planNameBounds = composeRule.onNodeWithText(planName)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(
            "培养方案标签与长名称上下排列",
            planNameBounds.top >= planLabelBounds.bottom,
        )
        assertTrue("方案名保持在卡片内容范围内", planNameBounds.left >= planRow.left && planNameBounds.right <= planRow.right)

        val refreshButton = composeRule.onNodeWithTag("datasources_refresh_button")
            .performScrollTo()
            .assertIsDisplayed()
        val buttonBounds = refreshButton.getUnclippedBoundsInRoot()
        val labelBounds = composeRule.onNodeWithTag(
            "datasources_refresh_button_label",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        assertTrue("大字体按钮保留 48dp 触控高度", buttonBounds.bottom - buttonBounds.top >= 48.dp)
        assertTrue("按钮文案完整落在按钮内", labelBounds.top >= buttonBounds.top && labelBounds.bottom <= buttonBounds.bottom)
    }
}
