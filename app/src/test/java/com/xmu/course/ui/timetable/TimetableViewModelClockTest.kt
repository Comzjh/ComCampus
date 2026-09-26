package com.xmu.course.ui.timetable

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.ViewWeekPreference
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class TimetableViewModelClockTest {

    @Test
    fun actualWeekRefreshesAtLocalMidnightAndWhenAppResumes() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        try {
            val clock = MutableClock(
                currentInstant = Instant.parse("2026-09-20T23:59:00Z"),
                currentZone = ZoneOffset.UTC,
            )
            val viewWeekPreference = RecordingViewWeekPreference()
            val viewModel = TimetableViewModel(
                application = ApplicationProvider.getApplicationContext<Application>(),
                repository = FakeTimetableFeatureRepository(
                    TimetableFeatureState(
                        timetable = Timetable(
                            id = 1L,
                            name = "synthetic",
                            semesterId = 1L,
                            startDate = "2026-09-07",
                            currentWeek = 1,
                            totalWeeks = 25,
                        ),
                        config = TimetableConfig(timetableId = 1L),
                    ),
                ),
                viewWeekPreference = viewWeekPreference,
                clock = clock,
            )

            runCurrent()
            assertEquals(2, viewModel.uiState.value.actualWeek)
            assertEquals(2, viewModel.uiState.value.viewWeek)

            viewModel.onForeground()
            runCurrent()
            clock.setInstant(Instant.parse("2026-09-21T00:00:00Z"))
            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals(3, viewModel.uiState.value.actualWeek)
            // Actual week changes without overwriting the user's saved browsing week.
            assertEquals(2, viewModel.uiState.value.viewWeek)
            assertEquals(0, viewWeekPreference.writeCount)

            viewModel.onBackground()
            clock.setInstant(Instant.parse("2026-09-28T08:00:00Z"))
            viewModel.onForeground()
            runCurrent()
            assertEquals(4, viewModel.uiState.value.actualWeek)
            viewModel.onBackground()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeTimetableFeatureRepository(
    private val state: TimetableFeatureState,
) : TimetableFeatureRepository {
    override fun observeCurrentTimetableState(): Flow<TimetableFeatureState> = flowOf(state)

    override suspend fun addCourse(course: Course) = Unit

    override suspend fun updateCourseColor(courseId: Long, color: String) = Unit

    override suspend fun updateCourseNote(courseId: Long, note: String) = Unit

    override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) = Unit

    override suspend fun updateConfig(config: TimetableConfig) = Unit
}

private class RecordingViewWeekPreference : ViewWeekPreference {
    var writeCount = 0
        private set

    override fun getViewWeek(timetableId: Long): Int? = null

    override fun setViewWeek(timetableId: Long, week: Int) {
        writeCount += 1
    }
}

private class MutableClock(
    private var currentInstant: Instant,
    private val currentZone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun setInstant(instant: Instant) {
        currentInstant = instant
    }
}
