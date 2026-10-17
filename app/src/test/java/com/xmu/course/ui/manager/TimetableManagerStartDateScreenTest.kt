package com.xmu.course.ui.manager

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp")
class TimetableManagerStartDateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var app: Application
    private lateinit var originalTimeZone: TimeZone
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        TimetablePrefs.setCurrent(app, 2L)
        originalTimeZone = TimeZone.getDefault()
        // A UTC-midnight picker value would become the previous date if converted using local time.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun tearDown() {
        store.clear()
        TimetablePrefs.setCurrent(app, null)
        TimeZone.setDefault(originalTimeZone)
        Dispatchers.resetMain()
    }

    @Test
    fun nullDateHasAVisibleButtonAndOpeningOrCancellingDoesNotSelectOrSave() {
        val source = TimetableStartDateTestFixture()
        val vm = showManager(source)

        composeRule.onNodeWithText("设置开学日期").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_start_date_1")
            .assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        composeRule.onNodeWithText("选择第1周周一，用于本周定位和课前提醒").assertIsDisplayed()
        assertTrue(source.savedDates.isEmpty())
        assertEquals(2L, vm.uiState.value.currentId)
        composeRule.onNodeWithTag("timetable_start_date_cancel").performClick()

        assertTrue(source.savedDates.isEmpty())
        assertEquals(2L, TimetablePrefs.currentTimetableId.value)
        composeRule.onNodeWithTag("timetable_start_date_save").assertDoesNotExist()
        composeRule.onNodeWithText("重命名", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("timetable_delete_confirm").assertDoesNotExist()
    }

    @Test
    fun explicitSaveOnANewDateUsesThisWeeksMondayWithoutTimezoneShift() {
        val source = TimetableStartDateTestFixture()
        val vm = showManager(source)

        composeRule.onNodeWithTag("timetable_start_date_1").performClick()
        assertTrue(source.savedDates.isEmpty())
        composeRule.onNodeWithTag("timetable_start_date_save").performClick()

        assertEquals(listOf(1L to "2026-09-21"), source.savedDates)
        assertEquals(2L, vm.uiState.value.currentId)
        composeRule.onNodeWithText("第 1 周 · 开学 2026-09-21").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_start_date_save").assertDoesNotExist()
    }

    @Test
    fun pickingAnotherCalendarDaySavesOnlyThatChoiceToThatCustomTimetable() {
        val source = TimetableStartDateTestFixture()
        val beforeSecond = source.summaries.value[1]
        showManager(source)

        composeRule.onNodeWithTag("timetable_start_date_1").performClick()
        composeRule.onNodeWithText("Monday, September 14, 2026").performClick()
        assertTrue("Selecting a day must not save before confirmation", source.savedDates.isEmpty())
        composeRule.onNodeWithTag("timetable_start_date_save").performClick()

        assertEquals(listOf(1L to "2026-09-14"), source.savedDates)
        assertEquals(beforeSecond, source.summaries.value[1])
        assertEquals(2L, TimetablePrefs.currentTimetableId.value)
    }

    @Test
    fun cancellingAnEditedSelectionDiscardsItWhenTheDialogIsReopened() {
        val source = TimetableStartDateTestFixture()
        showManager(source)

        composeRule.onNodeWithTag("timetable_start_date_1").performClick()
        composeRule.onNodeWithText("Monday, September 14, 2026").performClick()
        composeRule.onNodeWithTag("timetable_start_date_cancel").performClick()
        assertTrue(source.savedDates.isEmpty())
        composeRule.onNodeWithTag("timetable_start_date_1").performClick()
        composeRule.onNodeWithTag("timetable_start_date_save").performClick()

        assertEquals(listOf(1L to "2026-09-21"), source.savedDates)
    }

    @Test
    fun anExistingDateKeepsItsOriginalValueRatherThanDefaultingToThisMonday() {
        val source = TimetableStartDateTestFixture()
        showManager(source)

        composeRule.onNodeWithTag("timetable_start_date_2").performScrollTo().performClick()
        composeRule.onNodeWithTag("timetable_start_date_save").performClick()

        assertEquals(listOf(2L to "2026-09-07"), source.savedDates)
        assertEquals(null, source.summaries.value[0].timetable.startDate)
    }

    @Test
    fun importedTimetablesHaveNoCustomDateAction() {
        val imported = customStartDateSummary(3L, "2026-09-07").copy(isCustom = false)
        val source = TimetableStartDateTestFixture(listOf(imported))
        showManager(source)

        composeRule.onNodeWithTag("timetable_card_3").assertIsDisplayed()
        composeRule.onNodeWithTag("timetable_start_date_3").assertDoesNotExist()
        composeRule.onNodeWithText("修改开学日期").assertDoesNotExist()
        assertTrue(source.savedDates.isEmpty())
    }

    @Test
    fun dateButtonLongPressDoesNotOpenRenameDeleteOrSwitchTimetables() {
        val source = TimetableStartDateTestFixture()
        val vm = showManager(source)

        composeRule.onNodeWithTag("timetable_start_date_1").performTouchInput { longClick() }

        assertEquals(2L, vm.uiState.value.currentId)
        composeRule.onNodeWithText("重命名", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("timetable_delete_confirm").assertDoesNotExist()
        assertTrue(source.savedDates.isEmpty())
    }

    @Test
    fun cardSelectionAndLongPressManagementRemainAvailableAfterCancellingADate() {
        val source = TimetableStartDateTestFixture()
        val vm = showManager(source)
        composeRule.onNodeWithTag("timetable_start_date_1").performClick()
        composeRule.onNodeWithTag("timetable_start_date_cancel").performClick()

        composeRule.onNodeWithTag("timetable_card_1").performClick()
        assertEquals(1L, vm.uiState.value.currentId)
        // Press the title, away from the nested date button, to exercise the card's pointer handler.
        composeRule.onNodeWithText("合成自建课表 1", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("重命名", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("删除", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(source.savedDates.isEmpty())
    }

    private fun showManager(source: TimetableStartDateTestFixture): TimetableManagerViewModel {
        val vm = TimetableManagerViewModel(app, source, source::saveStartDate)
        store.put("vm", vm)
        composeRule.setContent {
            MaterialTheme {
                TimetableManagerScreen(
                    onBack = {},
                    viewModel = vm,
                    today = LocalDate.parse("2026-09-23"),
                )
            }
        }
        return vm
    }
}
