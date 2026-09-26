package com.xmu.course.ui.tronclass

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.data.tronclass.feature.TronClassAuthUiState
import com.xmu.course.data.tronclass.feature.TronClassCourseSyncUiResult
import com.xmu.course.data.tronclass.feature.TronClassFeatureRepository
import com.xmu.course.data.tronclass.feature.TronClassLogoutUiResult
import com.xmu.course.data.tronclass.feature.TronClassTodoSyncUiResult
import com.xmu.course.data.tronclass.model.TronClassUiError
import com.xmu.course.data.tronclass.model.TronClassUiErrorCategory
import com.xmu.course.data.tronclass.model.TronCourseUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TronClassScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: TronClassViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        viewModel = TronClassViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `未登录显示登录按钮并可触发登录`() {
        var loginClicked = false
        setContent(onLaunchAuth = { loginClicked = true })

        composeRule.onNodeWithText("登录畅课").assertIsDisplayed().performClick()

        assertTrue(loginClicked)
    }

    @Test
    fun `已登录显示同步按钮和课程列表`() {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.courses.value = listOf(sampleCourse())
        viewModel = TronClassViewModel(repository)
        setContent()

        composeRule.onNodeWithText("同步课程").assertIsDisplayed()
        composeRule.onNodeWithText("测试课程").assertIsDisplayed()
        composeRule.onNodeWithText("测试教师", substring = true).assertIsDisplayed()
    }

    @Test
    fun `同步错误显示安全错误文案`() {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Failed(
            TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
        )
        viewModel = TronClassViewModel(repository)
        setContent()

        composeRule.onNodeWithText("同步课程").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("网络连接失败，请检查网络后重试").assertIsDisplayed()
    }


    @Test
    fun `已登录显示已连接徽标`() {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        viewModel = TronClassViewModel(repository)
        setContent()

        composeRule.onNodeWithText("已连接").assertIsDisplayed()
    }

    @Test
    fun `会话过期显示登录已过期状态`() {
        repository.authState = TronClassAuthUiState.SESSION_EXPIRED
        viewModel = TronClassViewModel(repository)
        setContent()

        composeRule.onNodeWithText("登录已过期").assertIsDisplayed()
        composeRule.onNodeWithText("登录状态已失效，请重新登录").assertExists()
    }

    @Test
    fun `同步中显示正在同步状态`() {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncGate = CompletableDeferred()
        viewModel = TronClassViewModel(repository)
        setContent()

        composeRule.onNodeWithText("同步课程").performClick()

        composeRule.onNodeWithText("正在同步").assertIsDisplayed()
        composeRule.onNodeWithText("正在同步课程…").assertIsDisplayed()
        repository.syncGate!!.complete(Unit)
        composeRule.waitForIdle()
    }

    @Test
    fun `同步成功但无课程显示空态引导`() {
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(0)
        viewModel = TronClassViewModel(repository)
        setContent()

        composeRule.onNodeWithText("同步课程").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("同步完成，共 0 门课程").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("暂无畅课课程").performScrollTo().assertIsDisplayed()
    }
    @Test
    fun `同步成功后页面只上报一次完成事件`() {
        var completed = 0
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Success(1)
        viewModel = TronClassViewModel(repository)
        setContent(onSyncCompleted = { if (viewModel.claimSyncCompletion()) completed++ })

        composeRule.onNodeWithText("同步课程").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        assertEquals(1, completed)
    }

    @Test
    fun `同步失败页面上报不了完成事件`() {
        var completed = 0
        repository.authState = TronClassAuthUiState.AUTHENTICATED
        repository.syncResult = TronClassCourseSyncUiResult.Failed(
            TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
        )
        viewModel = TronClassViewModel(repository)
        setContent(onSyncCompleted = { if (viewModel.claimSyncCompletion()) completed++ })

        composeRule.onNodeWithText("同步课程").performClick()
        composeRule.waitForIdle()

        assertEquals(0, completed)
    }

    private fun setContent(onLaunchAuth: () -> Unit = {}, onSyncCompleted: () -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                TronClassScreen(
                    onLaunchAuth = onLaunchAuth,
                    onClearWebData = {},
                    viewModel = viewModel,
                    onSyncCompleted = onSyncCompleted,
                )
            }
        }
        composeRule.waitForIdle()
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
        var syncGate: CompletableDeferred<Unit>? = null

        override fun observeCourses() = courses

        override suspend fun refreshSession() = authState

        override suspend fun syncCourses(): TronClassCourseSyncUiResult {
            syncGate?.await()
            return syncResult
        }

        override suspend fun syncTodoSources() = TronClassTodoSyncUiResult.Success(0)

        override suspend fun logout() = TronClassLogoutUiResult.Success
    }
}
