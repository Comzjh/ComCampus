package com.xmu.course.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.SettingsDataSource
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.contracts.presentation.StartupDestination

import com.xmu.course.contracts.presentation.StartupDestinationPreference

import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.domain.Semester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private lateinit var source: FakeSettingsDataSource

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        source = FakeSettingsDataSource()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `导入学期后设置页面通过Room Flow自动刷新`() = runTest {
        val viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext(),
            source,
            FakeAssignmentSettings(),
            FakeTodoAutoSyncSettings(),
            FakeStartupDestinationPreference(),

        )

        assertNull(viewModel.uiState.value.semester)

        source.semesters.value = listOf(Semester(code = "20261", name = "2026-2027秋季学期"))

        val updatedState = withTimeout(2_000) {
            viewModel.uiState.first { it.semester?.name == "2026-2027秋季学期" }
        }
        assertEquals("2026-2027秋季学期", updatedState.semester?.name)
    }

    @Test
    fun `畅课作业开关默认关闭并可保存`() = runTest {
        val assignmentSettings = FakeAssignmentSettings()
        val viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext(),
            source,
            assignmentSettings,
            FakeTodoAutoSyncSettings(),
            FakeStartupDestinationPreference(),

        )

        assertEquals(false, viewModel.uiState.value.autoImportAssignments)
        viewModel.setAutoImportAssignments(true)
        assertEquals(true, viewModel.uiState.value.autoImportAssignments)
        assertEquals(true, assignmentSettings.enabled)
    }

    @Test

    fun `启动页默认首页且可保存修改`() = runTest {

        val startupPreference = FakeStartupDestinationPreference()

        val viewModel = SettingsViewModel(

            ApplicationProvider.getApplicationContext(),

            source,

            FakeAssignmentSettings(),

            FakeTodoAutoSyncSettings(),

            startupPreference,

        )

        assertEquals(StartupDestination.HOME, viewModel.uiState.value.startupDestination)

        viewModel.setStartupDestination(StartupDestination.TIMETABLE)

        assertEquals(StartupDestination.TIMETABLE, viewModel.uiState.value.startupDestination)

        assertEquals(StartupDestination.TIMETABLE, startupPreference.saved)

    }

    private class FakeSettingsDataSource : SettingsDataSource {
        val semesters = MutableStateFlow<List<Semester>>(emptyList())

        override fun observeSemesters(): Flow<List<Semester>> = semesters

        override suspend fun updateSemesterStartDate(semesterId: Long, startDate: String) = Unit

        override suspend fun deleteSemesterCourses(semesterId: Long) = Unit

        override suspend fun deleteSemester(semesterId: Long) = Unit
    }

    private class FakeAssignmentSettings : AssignmentSyncSettings {
        var enabled = false
        override fun isAutoImportEnabled(): Boolean = enabled
        override fun setAutoImportEnabled(enabled: Boolean) { this.enabled = enabled }
    }

    private class FakeStartupDestinationPreference : StartupDestinationPreference {

        var saved: StartupDestination? = null

        override fun destination(): StartupDestination? = saved

        override fun setDestination(destination: StartupDestination) { saved = destination }

    }

    private class FakeTodoAutoSyncSettings : TodoAutoSyncSettings {
        private var enabled = true
        override fun isEnabled(): Boolean = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun lastSuccessfulSyncAt(): Long? = null
        override fun setLastSuccessfulSyncAt(timestamp: Long) = Unit
    }
}
