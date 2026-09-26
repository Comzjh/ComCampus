package com.xmu.course.ui.tronclass

import android.app.Activity
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.data.tronclass.model.TronCourseUiModel
import com.xmu.course.data.tronclass.model.TronClassUiError
import com.xmu.course.data.tronclass.model.TronClassUiErrorCategory
import com.xmu.course.data.tronclass.feature.TronClassAuthUiState
import com.xmu.course.data.tronclass.feature.TronClassCourseSyncUiResult
import com.xmu.course.data.tronclass.feature.TronClassFeatureRepository
import com.xmu.course.data.tronclass.feature.TronClassLogoutUiResult
import com.xmu.course.data.tronclass.feature.TronClassTodoSyncUiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TronClassViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: TronClassViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        viewModel = TronClassViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `未登录显示未认证状态`() = runTest {
        advanceUntilIdle()

        assertEquals(TronClassScreenState.Unauthenticated, viewModel.uiState.value.screenState)
    }

    @Test
    fun `登录成功后刷新为已认证状态`() = runTest {
        advanceUntilIdle()
        repository.authState = TronClassAuthUiState.AUTHENTICATED

        viewModel.onAuthResult(Activity.RESULT_OK)
        advanceUntilIdle()

        assertEquals(TronClassScreenState.Authenticated, viewModel.uiState.value.screenState)
    }

    @Test
    fun `同步成功显示数量状态`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(2, lastSyncTime = 20L)
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertEquals(TronClassScreenState.Success, viewModel.uiState.value.screenState)
        assertEquals(2, viewModel.uiState.value.lastSyncCount)
        assertTrue(repository.syncCalled)
    }

    @Test
    fun `开启作业开关后同步课程会同步作业`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(2)
        repository.todoSyncResult = TronClassTodoSyncUiResult.Success(3)
        val settings = FakeAssignmentSettings(enabled = true)
        viewModel = TronClassViewModel(repository, settings)
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.lastAssignmentSyncCount)
        assertTrue(repository.assignmentSyncCalled)
    }

    @Test
    fun `显式关闭作业导入后同步课程不再导作业`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(2)
        repository.todoSyncResult = TronClassTodoSyncUiResult.Success(3)
        viewModel = TronClassViewModel(repository, FakeAssignmentSettings(enabled = false))
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertEquals(TronClassScreenState.Success, viewModel.uiState.value.screenState)
        assertFalse(repository.assignmentSyncCalled)
    }

    @Test
    fun `作业同步401映射为SessionExpired`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(1)
        repository.todoSyncResult = TronClassTodoSyncUiResult.SessionExpired
        viewModel = TronClassViewModel(repository, FakeAssignmentSettings(enabled = true))
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertEquals(TronClassScreenState.SessionExpired, viewModel.uiState.value.screenState)
    }

    @Test
    fun `同步失败显示安全错误并保留课程`() = runTest {
        val old = sampleCourse()
        repository.courses.value = listOf(old)
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Failed(
            TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
        )
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertEquals(TronClassScreenState.Error, viewModel.uiState.value.screenState)
        assertEquals(
            TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
            viewModel.uiState.value.error,
        )
        assertEquals(
            listOf(
                TronCourseUiModel(
                    id = old.id,
                    name = old.name,
                    instructor = old.instructor,
                    semester = old.semester,
                ),
            ),
            viewModel.uiState.value.courses,
        )
    }

    @Test
    fun `SessionExpired显示重新登录状态`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.SessionExpired
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertEquals(TronClassScreenState.SessionExpired, viewModel.uiState.value.screenState)
    }

    @Test
    fun `退出登录清除课程并请求清理WebView`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.courses.value = listOf(sampleCourse())
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.confirmLogout()
        advanceUntilIdle()

        assertEquals(TronClassScreenState.Unauthenticated, viewModel.uiState.value.screenState)
        assertTrue(viewModel.uiState.value.courses.isEmpty())
        assertTrue(viewModel.uiState.value.clearWebDataRequested)
        assertTrue(repository.logoutCalled)
    }

    @Test
    fun `同步成功只允许被导航层认领一次`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(2)
        viewModel.refreshSession()
        advanceUntilIdle()

        viewModel.syncCourses()
        advanceUntilIdle()

        assertTrue(viewModel.claimSyncCompletion())
        assertFalse("重复回调不得再次回待办", viewModel.claimSyncCompletion())
    }

    @Test
    fun `失败不产生成功事件且下次成功仍可认领`() = runTest {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Failed(
            TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
        )
        viewModel.refreshSession()
        advanceUntilIdle()
        viewModel.syncCourses()
        advanceUntilIdle()
        assertFalse("失败必须留在原页", viewModel.claimSyncCompletion())

        repository.syncResult = TronClassCourseSyncUiResult.Success(1)
        viewModel.syncCourses()
        advanceUntilIdle()
        assertTrue(viewModel.claimSyncCompletion())
        assertFalse(viewModel.claimSyncCompletion())
    }

    private fun sampleCourse() = TronCourseUiModel(
        id = 1L,
        name = "测试课程",
        semester = "测试学期",
        instructor = "测试教师",
    )

    private class FakeRepository : TronClassFeatureRepository {
        val courses = MutableStateFlow<List<TronCourseUiModel>>(emptyList())
        var authState: TronClassAuthUiState = TronClassAuthUiState.UNAUTHENTICATED
        var syncResult: TronClassCourseSyncUiResult = TronClassCourseSyncUiResult.Success(0)
        var syncCalled = false
        var assignmentSyncCalled = false
        var todoSyncResult: TronClassTodoSyncUiResult = TronClassTodoSyncUiResult.Success(0)
        var logoutCalled = false

        override fun observeCourses() = courses

        override suspend fun refreshSession() = authState

        override suspend fun syncCourses(): TronClassCourseSyncUiResult {
            syncCalled = true
            return syncResult
        }

        override suspend fun syncTodoSources(): TronClassTodoSyncUiResult {
            assignmentSyncCalled = true
            return todoSyncResult
        }

        override suspend fun logout(): TronClassLogoutUiResult {
            logoutCalled = true
            courses.value = emptyList()
            return TronClassLogoutUiResult.Success
        }
    }

    private class FakeAssignmentSettings(private var enabled: Boolean) : AssignmentSyncSettings {
        override fun isAutoImportEnabled(): Boolean = enabled
        override fun setAutoImportEnabled(enabled: Boolean) { this.enabled = enabled }
    }
}
