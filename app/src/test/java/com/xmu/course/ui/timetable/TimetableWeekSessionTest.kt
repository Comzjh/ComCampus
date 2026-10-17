package com.xmu.course.ui.timetable

import android.app.Application
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableWeekSessionTest {
    @Test
    fun coldStartAndNewViewModelIgnoreLastSessionsSavedWeek() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val preference = SessionTestWeekPreference(mutableMapOf(1L to 12))
            val source = SessionTestTimetableRepository(sessionTestState())
            val clock = SessionTestClock()
            fun newViewModel(key: String) = TimetableViewModel(
                ApplicationProvider.getApplicationContext<Application>(), source, preference, clock = clock,
            ).also { store.put(key, it) }

            val first = newViewModel("first")
            runCurrent()
            assertEquals(3, first.uiState.value.actualWeek)
            assertEquals(3, first.uiState.value.viewWeek)
            first.selectWeek(10)
            assertEquals(10, first.uiState.value.viewWeek)

            val reopened = newViewModel("reopened")
            runCurrent()
            assertEquals(3, reopened.uiState.value.viewWeek)
            assertEquals("Cold starts must not read a previous session's week", 0, preference.readCount)
            assertEquals("Browsing weeks are session state", 0, preference.writeCount)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun returningInTheSameCalendarWeekResetsManualBrowsing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val vm = sessionTestViewModel(SessionTestTimetableRepository(sessionTestState()), store)
            runCurrent()
            vm.onForeground()
            vm.selectWeek(12)
            vm.onBackground()
            vm.onForeground()
            runCurrent()

            assertEquals(3, vm.uiState.value.actualWeek)
            assertEquals(3, vm.uiState.value.viewWeek)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun returningAfterWeekBoundaryUsesNewCurrentWeek() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val clock = SessionTestClock()
            val vm = sessionTestViewModel(SessionTestTimetableRepository(sessionTestState()), store, clock)
            runCurrent()
            vm.onForeground()
            vm.selectWeek(12)
            vm.onBackground()
            clock.current = Instant.parse("2026-09-28T08:00:00Z")
            vm.onForeground()
            runCurrent()

            assertEquals(4, vm.uiState.value.actualWeek)
            assertEquals(4, vm.uiState.value.viewWeek)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dataUpdatesAndDuplicateForegroundCallbacksKeepManualWeek() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val source = SessionTestTimetableRepository(sessionTestState())
            val vm = sessionTestViewModel(source, store)
            runCurrent()
            vm.onForeground()
            vm.selectWeek(10)
            source.states.value = source.states.value.copy(
                courses = listOf(Course(id = 9L, name = "Synthetic updated class", dayOfWeek = 1,
                    startSection = 1, duration = 2, weeks = setOf(10))),
                config = TimetableConfig(timetableId = 1L, showNonCurrentWeek = false),
            )
            runCurrent()
            vm.onForeground()
            assertEquals(10, vm.uiState.value.viewWeek)
            assertEquals(3, vm.uiState.value.actualWeek)
            assertEquals("Synthetic updated class", vm.uiState.value.courses.single().name)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun tableSwitchesKeepSeparatePositionsOnlyWithinForegroundSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val a = sessionTestState()
            val b = sessionTestState(id = 2L, startDate = "2026-09-14")
            val source = SessionTestTimetableRepository(a)
            val vm = sessionTestViewModel(source, store)
            runCurrent()
            vm.onForeground()
            vm.selectWeek(10)
            source.states.value = b
            runCurrent()
            assertEquals(2, vm.uiState.value.viewWeek)
            vm.selectWeek(7)
            source.states.value = a
            runCurrent()
            assertEquals(10, vm.uiState.value.viewWeek)
            source.states.value = b
            runCurrent()
            assertEquals(7, vm.uiState.value.viewWeek)

            vm.onBackground()
            vm.onForeground()
            assertEquals(2, vm.uiState.value.viewWeek)
            source.states.value = a
            runCurrent()
            assertEquals(3, vm.uiState.value.viewWeek)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun foregroundBeforeDataArrivesStillStartsAtCurrentWeek() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val source = SessionTestTimetableRepository(TimetableFeatureState())
            val vm = sessionTestViewModel(source, store)
            vm.onForeground()
            runCurrent()
            vm.selectWeek(1) // A loading Pager must not establish a browsing position.
            source.states.value = sessionTestState()
            runCurrent()
            assertEquals(3, vm.uiState.value.viewWeek)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private fun sessionTestViewModel(
    source: SessionTestTimetableRepository,
    store: ViewModelStore,
    clock: Clock = SessionTestClock(),
) = TimetableViewModel(
    ApplicationProvider.getApplicationContext<Application>(), source,
    SessionTestWeekPreference(mutableMapOf(1L to 12, 2L to 15)), clock = clock,
).also { store.put("vm", it) }

private fun sessionTestState(id: Long = 1L, startDate: String = "2026-09-07") = TimetableFeatureState(
    timetable = Timetable(id = id, name = "Synthetic table $id", semesterId = id,
        startDate = startDate, totalWeeks = 25),
    config = TimetableConfig(timetableId = id),
)

private class SessionTestTimetableRepository(initial: TimetableFeatureState) : TimetableFeatureRepository {
    val states = MutableStateFlow(initial)
    override fun observeCurrentTimetableState() = states
    override suspend fun addCourse(course: Course) = Unit
    override suspend fun updateCourseColor(courseId: Long, color: String) = Unit
    override suspend fun updateCourseNote(courseId: Long, note: String) = Unit
    override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) = Unit
    override suspend fun updateConfig(config: TimetableConfig) = Unit
}

private class SessionTestWeekPreference(private val saved: MutableMap<Long, Int>) : ViewWeekPreference {
    var readCount = 0
    var writeCount = 0
    override fun getViewWeek(timetableId: Long): Int? {
        readCount += 1
        return saved[timetableId]
    }
    override fun setViewWeek(timetableId: Long, week: Int) {
        writeCount += 1
        saved[timetableId] = week
    }
}

private class SessionTestClock(var current: Instant = Instant.parse("2026-09-21T08:00:00Z")) : Clock() {
    override fun instant() = current
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
}
