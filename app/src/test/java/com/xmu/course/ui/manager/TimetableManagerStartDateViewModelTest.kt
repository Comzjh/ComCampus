package com.xmu.course.ui.manager

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableManagerStartDateViewModelTest {
    private lateinit var app: Application
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        TimetablePrefs.setCurrent(app, 1L)
    }

    @After
    fun tearDown() {
        store.clear()
        TimetablePrefs.setCurrent(app, null)
        Dispatchers.resetMain()
    }

    @Test
    fun injectedSaveUpdatesOnlyTheTargetCustomDateAndPreservesAllOtherFields() {
        val source = TimetableStartDateTestFixture()
        val before = source.summaries.value
        val vm = viewModel(source)

        vm.setStartDate(2L, "2026-09-14")

        assertEquals(listOf(2L to "2026-09-14"), source.savedDates)
        assertEquals(before[0], vm.uiState.value.timetables[0])
        assertEquals(before[1].copy(timetable = before[1].timetable.copy(startDate = "2026-09-14")),
            vm.uiState.value.timetables[1])
        assertEquals(1L, vm.uiState.value.currentId)
        assertEquals(1L, TimetablePrefs.currentTimetableId.value)
        assertEquals("开学日期已更新", vm.uiState.value.message)
    }

    @Test
    fun importedMissingAndRemovedTimetablesCannotBeSaved() {
        val imported = customStartDateSummary(3L, "2026-09-07").copy(isCustom = false)
        val source = TimetableStartDateTestFixture(listOf(customStartDateSummary(1L), imported))
        val vm = viewModel(source)

        vm.setStartDate(3L, "2026-09-14")
        vm.setStartDate(99L, "2026-09-14")
        source.summaries.value = listOf(imported)
        vm.setStartDate(1L, "2026-09-14")

        assertTrue(source.savedDates.isEmpty())
        assertEquals(listOf(imported), source.summaries.value)
        assertEquals("只能设置已有自建课表的开学日期", vm.uiState.value.message)
    }

    @Test
    fun invalidDatesNeverReachTheWriterButARealLeapDayDoes() {
        val source = TimetableStartDateTestFixture()
        val vm = viewModel(source)
        listOf("", "2026-02-30", "2025-02-29", "2026-13-01", "2026/09/07", "2026-9-7").forEach { date ->
            vm.setStartDate(1L, date)
            assertTrue("Invalid date must not be saved: $date", source.savedDates.isEmpty())
            assertEquals("请选择有效的开学日期", vm.uiState.value.message)
        }

        vm.setStartDate(1L, "2028-02-29")
        assertEquals(listOf(1L to "2028-02-29"), source.savedDates)
        assertEquals("2028-02-29", vm.uiState.value.timetables.first().timetable.startDate)
    }

    @Test
    fun writerFailureReportsFailureWithoutOptimisticallyChangingTheDate() {
        val source = TimetableStartDateTestFixture()
        val before = source.summaries.value
        val vm = viewModel(source) { _, _ -> throw IllegalStateException("Synthetic save failure") }

        vm.setStartDate(1L, "2026-09-21")

        assertEquals(before, vm.uiState.value.timetables)
        assertEquals("开学日期保存失败，请重试", vm.uiState.value.message)
    }

    @Test
    fun cancellationCancelsTheSaveJobAndIsNotConvertedToAFailureMessage() {
        val source = TimetableStartDateTestFixture()
        var saveJob: Job? = null
        val vm = viewModel(source) { _, _ ->
            saveJob = currentCoroutineContext().job
            throw CancellationException("Synthetic cancellation")
        }

        vm.setStartDate(1L, "2026-09-21")

        assertTrue("Cancellation must escape the save handler", saveJob?.isCancelled == true)
        assertNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.timetables.first().timetable.startDate)
    }

    @Test
    fun oldTwoArgumentConstructorRemainsUsableAndDoesNotReportAFakeSave() {
        val source = TimetableStartDateTestFixture()
        val vm = TimetableManagerViewModel(app, source).also { store.put("legacy", it) }

        vm.setStartDate(1L, "2026-09-21")

        assertTrue(source.savedDates.isEmpty())
        assertEquals("暂时无法保存开学日期，请稍后重试", vm.uiState.value.message)
        assertNull(vm.uiState.value.timetables.first().timetable.startDate)
    }

    private fun viewModel(
        source: TimetableStartDateTestFixture,
        save: suspend (Long, String) -> Unit = source::saveStartDate,
    ) = TimetableManagerViewModel(app, source, save).also { store.put("vm", it) }
}
