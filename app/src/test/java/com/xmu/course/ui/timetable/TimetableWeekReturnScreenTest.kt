package com.xmu.course.ui.timetable

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.ViewWeekPreference
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Real TimetableScreen/HorizontalPager coverage; no copied Pager or week calculation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableWeekReturnScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val store = ViewModelStore()
    private val source = PagerTestRepository()
    private val clock = PagerTestClock()
    private val preference = PagerTestPreference()
    private lateinit var vm: TimetableViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = TimetableViewModel(
            ApplicationProvider.getApplicationContext<Application>(), source, preference, clock = clock,
        )
        store.put("vm", vm)
        vm.onForeground()
    }

    @After
    fun teardown() {
        vm.onBackground()
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun delayedColdStartPositionsPagerAtCurrentWeekWithoutWritingInitialPageOne() {
        showScreen()
        composeRule.onNodeWithTag("timetable_empty").assertIsDisplayed()
        composeRule.runOnIdle { source.states.value = pagerTestState().copy(courses = emptyList()) }
        composeRule.onNodeWithTag("timetable_no_courses").assertIsDisplayed()
        composeRule.onNodeWithText("第3周 · 本周").assertIsDisplayed()
        composeRule.runOnIdle { source.states.value = pagerTestState() }

        assertVisibleWeek(3, current = true)
        composeRule.runOnIdle {
            assertEquals(3, vm.uiState.value.viewWeek)
            assertEquals(0, preference.readCount)
            assertEquals("Initial Pager state must not overwrite the week", 0, preference.writeCount)
        }
    }

    @Test
    fun manualPagerBrowsingSurvivesDataUpdatesThenResumeShowsNewCurrentWeek() {
        source.states.value = pagerTestState()
        showScreen()
        assertVisibleWeek(3, current = true)

        composeRule.onNodeWithTag("timetable_week_pager").performScrollToIndex(9)
        assertVisibleWeek(10)
        composeRule.runOnIdle {
            assertEquals(10, vm.uiState.value.viewWeek)
            source.states.value = source.states.value.copy(
                courses = source.states.value.courses.map { it.copy(note = "Synthetic update") },
            )
        }
        assertVisibleWeek(10)

        composeRule.runOnIdle {
            vm.onBackground()
            clock.current = Instant.parse("2026-09-28T08:00:00Z")
            vm.onForeground()
        }
        assertVisibleWeek(4, current = true)
        composeRule.runOnIdle { assertEquals(4, vm.uiState.value.viewWeek) }
    }

    @Test
    fun weekPickerAndSameWeekResumeBothReachTheRequestedPagerPage() {
        source.states.value = pagerTestState()
        showScreen()
        assertVisibleWeek(3, current = true)

        composeRule.onNodeWithTag("timetable_header_week").performClick()
        composeRule.onNodeWithTag("week_picker_grid").performScrollToIndex(7)
        // The modal lives in a separate Android window; use its accessibility click action
        // rather than test-host coordinates, then verify the real Pager and header.
        composeRule.onNodeWithTag("week_picker_week_8")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(8, vm.uiState.value.viewWeek) }
        assertVisibleWeek(8)
        composeRule.runOnIdle {
            assertEquals(8, vm.uiState.value.viewWeek)
            vm.onBackground()
            vm.onForeground()
        }
        assertVisibleWeek(3, current = true)
        composeRule.runOnIdle { assertEquals(3, vm.uiState.value.viewWeek) }
    }

    @Test
    fun switchingTablesRestoresOnlyThisSessionsBrowsedPages() {
        val a = pagerTestState()
        val b = pagerTestState(id = 2L, startDate = "2026-09-14")
        source.states.value = a
        showScreen()
        assertVisibleWeek(3, current = true)
        composeRule.onNodeWithTag("timetable_week_pager").performScrollToIndex(9)
        assertVisibleWeek(10)

        composeRule.runOnIdle { source.states.value = b }
        assertVisibleWeek(2, current = true)
        composeRule.onNodeWithTag("timetable_week_pager").performScrollToIndex(6)
        assertVisibleWeek(7)
        composeRule.runOnIdle { source.states.value = a }
        assertVisibleWeek(10)
    }

    private fun showScreen() {
        composeRule.setContent { MaterialTheme { TimetableScreen(viewModel = vm) } }
    }

    private fun assertVisibleWeek(week: Int, current: Boolean = false) {
        composeRule.onNodeWithTag("timetable_week_page_$week").assertIsDisplayed()
        val suffix = if (current) " · 本周" else ""
        composeRule.onNodeWithText("第${week}周$suffix").assertIsDisplayed()
    }
}

private fun pagerTestState(id: Long = 1L, startDate: String = "2026-09-07") = TimetableFeatureState(
    timetable = Timetable(id = id, name = "Synthetic table $id", semesterId = id,
        startDate = startDate, totalWeeks = 25),
    courses = listOf(Course(id = id, semesterId = id, name = "Synthetic class", dayOfWeek = 1,
        startSection = 1, duration = 2, weeks = (1..25).toSet())),
    config = TimetableConfig(timetableId = id, headerCompactMode = true),
)

private class PagerTestRepository : TimetableFeatureRepository {
    val states = MutableStateFlow(TimetableFeatureState())
    override fun observeCurrentTimetableState() = states
    override suspend fun addCourse(course: Course) = Unit
    override suspend fun updateCourseColor(courseId: Long, color: String) = Unit
    override suspend fun updateCourseNote(courseId: Long, note: String) = Unit
    override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) = Unit
    override suspend fun updateConfig(config: TimetableConfig) = Unit
}

private class PagerTestPreference : ViewWeekPreference {
    var readCount = 0
    var writeCount = 0
    override fun getViewWeek(timetableId: Long): Int? {
        readCount += 1
        return 12
    }
    override fun setViewWeek(timetableId: Long, week: Int) { writeCount += 1 }
}

private class PagerTestClock(var current: Instant = Instant.parse("2026-09-21T08:00:00Z")) : Clock() {
    override fun instant() = current
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
}
