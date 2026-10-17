package com.xmu.course.ui.settings

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.BackgroundType
import com.xmu.course.ui.background.BackgroundPickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room writes are held briefly to model a save whose observer has not refreshed yet. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = XmuCourseApplication::class)
class TimetableSettingsSaveRegressionTest {
    private lateinit var app: XmuCourseApplication
    private val scopes = mutableListOf<kotlinx.coroutines.CoroutineScope>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.closeAndResetForTest()
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("xmu_course.db")
        TimetablePrefs.setCurrent(app, null)
    }

    @After
    fun teardown() {
        scopes.forEach { it.cancel() }
        TimetablePrefs.setCurrent(app, null)
        AppDatabase.closeAndResetForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun consecutiveDisplayChangesBothSurvivePendingDatabaseSave() = runTest {
        val repo = app.appContainer.timetableRepository
        val timetable = repo.createTimetable("Synthetic display settings")
        TimetablePrefs.setCurrent(app, timetable.id)
        val viewModel = TimetableSettingsViewModel(app)
        scopes += viewModel.viewModelScope
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { viewModel.uiState.first { it.config != null } }
        }
        val observerJobs = viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toSet()

        app.appContainer.database.withTransaction {
            viewModel.update { it.copy(showSaturday = true) }
            viewModel.update { it.copy(showSunday = true) }
        }
        viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children
            .filterNot { it in observerJobs }.toList().joinAll()

        val saved = repo.getConfig(timetable.id)
        assertTrue("The Saturday switch must survive the following Sunday save", saved.showSaturday)
        assertTrue("The Sunday switch must also persist", saved.showSunday)
    }

    @Test
    fun selectingBackgroundThenAdjustingOverlayKeepsTheSelectedBackground() = runTest {
        val repo = app.appContainer.timetableRepository
        val timetable = repo.createTimetable("Synthetic background settings")
        TimetablePrefs.setCurrent(app, timetable.id)
        val viewModel = BackgroundPickerViewModel(app)
        scopes += viewModel.viewModelScope
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { viewModel.state.first { it.config != null } }
        }
        val observerJobs = viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toSet()

        app.appContainer.database.withTransaction {
            viewModel.selectSolid("#FF112233")
            viewModel.update { it.copy(overlayAlpha = 0.35f) }
        }
        viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.children
            .filterNot { it in observerJobs }.toList().joinAll()

        val saved = repo.getConfig(timetable.id)
        assertEquals("Changing overlay must not reset the chosen background", BackgroundType.SOLID, saved.backgroundType)
        assertEquals("#FF112233", saved.backgroundValue)
        assertEquals(0.35f, saved.overlayAlpha, 0.0001f)
    }
}
