package com.xmu.course.ui.manager

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.TimetableManagementContract
import com.xmu.course.contracts.TimetableSummary
import com.xmu.course.domain.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDate

private class FixedTimetableManagementContract(
    private val summary: TimetableSummary,
) : TimetableManagementContract {
    override fun observeTimetables(): Flow<List<TimetableSummary>> = flowOf(listOf(summary))
    override suspend fun createTimetable(name: String): Timetable = summary.timetable
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun deleteTimetable(id: Long) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableManagerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun managerShowsActualTeachingWeekInsteadOfStaleStoredWeek() {
        val timetable = Timetable(
            id = 9_876_543L,
            name = "合成学期",
            semesterId = 1L,
            startDate = "2026-09-07",
            totalWeeks = 25,
            currentWeek = 1,
        )
        val viewModel = TimetableManagerViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            FixedTimetableManagementContract(TimetableSummary(timetable, courseCount = 4, isCustom = false)),
        )

        composeRule.setContent {
            MaterialTheme {
                TimetableManagerScreen(
                    onBack = {},
                    viewModel = viewModel,
                    today = LocalDate.parse("2026-09-23"),
                )
            }
        }

        composeRule.onNodeWithText("第 3 周 · 开学 2026-09-07").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("第 1 周 · 开学 2026-09-07").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun singleTimetableShowsManagementHintAndIrreversibleDeleteConfirmation() {
        val timetable = Timetable(
            id = 9_876_543L,
            name = "合成学期",
            semesterId = 1L,
            startDate = "2026-09-07",
            totalWeeks = 25,
            currentWeek = 1,
        )
        val viewModel = TimetableManagerViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            FixedTimetableManagementContract(TimetableSummary(timetable, courseCount = 4, isCustom = true)),
        )
        composeRule.setContent {
            MaterialTheme {
                TimetableManagerScreen(
                    onBack = {},
                    viewModel = viewModel,
                    today = LocalDate.parse("2026-09-23"),
                )
            }
        }

        composeRule.onNodeWithTag("timetable_management_hint").assertIsDisplayed()
        composeRule.onNodeWithText("长按课表可重命名或删除").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_card_${timetable.id}")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("删除", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("此操作不可撤销", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_delete_confirm").assertIsDisplayed()
    }
}
