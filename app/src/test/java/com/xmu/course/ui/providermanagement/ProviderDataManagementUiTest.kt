package com.xmu.course.ui.providermanagement

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xmu.course.contracts.provider.AuthState
import com.xmu.course.contracts.provider.AuthStateSource
import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.ProviderManagementEntry
import com.xmu.course.contracts.provider.SyncPolicy
import com.xmu.course.data.jwgrades.JwGradeDataOwner
import com.xmu.course.data.jwgrades.JwGradeStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private class FakeDataOwner(initialCount: Int) : PrivacyDataOwner {
    var currentCount: Int = initialCount
    var clearCalls: Int = 0

    override suspend fun countLocalData(): Int = currentCount
    override suspend fun clearLocalData() { clearCalls++; currentCount = 0 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDataManagementUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entryWithOwner(owner: PrivacyDataOwner?) = ProviderManagementEntry(
        descriptor = ProviderDescriptor(
            "xmu.wisedu",
            setOf(ProviderCapability.TIMETABLE),
            SyncPolicy.MANUAL_ONLY,
        ),
        statusSource = object : AuthStateSource {
            override fun currentState(): AuthState = AuthState.AUTHENTICATED
        },
        dataOwner = owner,
    )

    @Test
    fun cardShowsManageEntryOnlyWhenDataOwnerExists() {
        val entries = listOf(
            entryWithOwner(FakeDataOwner(5)),
            ProviderManagementEntry(
                descriptor = ProviderDescriptor(
                    "xmu.jw",
                    setOf(ProviderCapability.CAMPUS_SERVICE),
                    SyncPolicy.MANUAL_ONLY,
                ),
            ),
        )
        composeRule.setContent {
            ProviderManagementScreen(
                viewModel = ProviderManagementViewModel(entries),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("管理数据").assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithText("管理数据").fetchSemanticsNodes().size)
    }

    @Test
    fun dataManagementScreenShowsCount() {
        composeRule.setContent {
            ProviderDataManagementScreen(
                viewModel = ProviderDataManagementViewModel(entryWithOwner(FakeDataOwner(23))),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("本机数据：23 条").assertIsDisplayed()
        composeRule.onNodeWithText("删除前会再确认一次，只影响这台手机上的该来源数据。").assertIsDisplayed()
    }

    @Test
    fun deleteDialogCancelDoesNotDelete() {
        val owner = FakeDataOwner(23)
        composeRule.setContent {
            ProviderDataManagementScreen(
                viewModel = ProviderDataManagementViewModel(entryWithOwner(owner)),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("删除此来源数据").performClick()
        composeRule.onNodeWithText("将删除当前来源的 23 条本机数据，不可恢复。其他来源的数据不受影响。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        // 取消：不删除、计数不变
        assertEquals(0, owner.clearCalls)
        composeRule.onNodeWithText("本机数据：23 条").assertIsDisplayed()
    }

    @Test
    fun deleteDialogConfirmDeletesAndRefreshesCount() {
        val owner = FakeDataOwner(23)
        composeRule.setContent {
            ProviderDataManagementScreen(
                viewModel = ProviderDataManagementViewModel(entryWithOwner(owner)),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("删除此来源数据").performClick()
        composeRule.onNodeWithText("确认删除").performClick()

        // 确认：clearLocalData 调用一次，count 刷新为 0
        assertEquals(1, owner.clearCalls)
        composeRule.onNodeWithText("本机暂无该来源数据").assertIsDisplayed()
        composeRule.onNodeWithText("已删除该来源的本机数据").assertIsDisplayed()
    }

    @Test
    fun deleteButtonDisabledWhenNoLocalData() {
        composeRule.setContent {
            ProviderDataManagementScreen(
                viewModel = ProviderDataManagementViewModel(entryWithOwner(FakeDataOwner(0))),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("本机暂无该来源数据").assertIsDisplayed()
        composeRule.onNodeWithText("删除此来源数据").assertIsNotEnabled()
    }

    @Test
    fun unreadableGradeCacheCanBeClearedAfterConfirmation() {
        val file = tempFolder.newFile("grades.json").apply { writeText("{not-json") }
        val viewModel = ProviderDataManagementViewModel("xmu.jw", JwGradeDataOwner(JwGradeStore(file)))
        composeRule.setContent {
            ProviderDataManagementScreen(viewModel = viewModel, onBack = {}, title = "成绩缓存")
        }

        composeRule.onNodeWithText("本机数据暂时读不到").assertIsDisplayed()
        composeRule.onNodeWithText("删除此来源数据").assertIsEnabled().performClick()
        composeRule.onNodeWithText(
            "当前来源的数据数量暂时无法读取。确认后会尝试删除该来源全部本机数据，不可恢复。其他来源的数据不受影响。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        assertTrue(file.exists())

        composeRule.onNodeWithText("删除此来源数据").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        assertFalse(file.exists())
        composeRule.onNodeWithText("本机暂无该来源数据").assertIsDisplayed()
        composeRule.onNodeWithText("已删除该来源的本机数据").assertIsDisplayed()
    }

    @Test
    fun failedGradeCacheDeletionShowsFailureInsteadOfSuccess() {
        val file = tempFolder.newFile("grades.json")
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        val blocker = File(file, "keep").apply { writeText("synthetic blocker") }
        val viewModel = ProviderDataManagementViewModel("xmu.jw", JwGradeDataOwner(JwGradeStore(file)))
        composeRule.setContent {
            ProviderDataManagementScreen(viewModel = viewModel, onBack = {}, title = "成绩缓存")
        }

        composeRule.onNodeWithText("删除此来源数据").assertIsEnabled().performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        assertTrue(blocker.exists())
        composeRule.onNodeWithText("删除失败，请稍后重试").assertIsDisplayed()
        composeRule.onNodeWithText("本机数据暂时读不到").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("已删除该来源的本机数据").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun confirmedDeleteContinuesAndFailureIsShownAfterLeavingAndReenteringScreen() {
        val deleteStarted = CompletableDeferred<Unit>()
        val allowDeleteToFail = CompletableDeferred<Unit>()
        val owner = object : PrivacyDataOwner {
            var clearCalls = 0

            override suspend fun countLocalData(): Int = 23

            override suspend fun clearLocalData() {
                clearCalls++
                deleteStarted.complete(Unit)
                allowDeleteToFail.await()
                throw IllegalStateException("synthetic delete failure")
            }
        }
        val showScreen = mutableStateOf(true)
        var activeViewModel: ProviderDataManagementViewModel? = null

        composeRule.setContent {
            val appOwner = checkNotNull(LocalViewModelStoreOwner.current)
            if (showScreen.value) {
                val viewModel: ProviderDataManagementViewModel = viewModel(
                    viewModelStoreOwner = appOwner,
                    key = "provider-data-management:xmu.wisedu",
                    factory = remember {
                        ProviderDataManagementViewModelFactory("xmu.wisedu", owner)
                    },
                )
                SideEffect { activeViewModel = viewModel }
                ProviderDataManagementScreen(
                    viewModel = viewModel,
                    onBack = { showScreen.value = false },
                    title = "合成来源",
                )
            } else {
                Button(onClick = { showScreen.value = true }) {
                    Text("重新进入")
                }
            }
        }

        composeRule.onNodeWithText("删除此来源数据").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        assertTrue(deleteStarted.isCompleted)
        val firstViewModel = checkNotNull(activeViewModel)
        assertTrue(firstViewModel.deleting.value)

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("重新进入").assertIsDisplayed()
        assertTrue(firstViewModel.deleting.value)

        composeRule.onNodeWithText("重新进入").performClick()
        composeRule.onNodeWithText("删除中…").assertIsNotEnabled()
        assertTrue(checkNotNull(activeViewModel) === firstViewModel)
        assertEquals(1, owner.clearCalls)

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("重新进入").assertIsDisplayed()

        allowDeleteToFail.complete(Unit)
        composeRule.runOnIdle { }
        assertEquals("删除失败，请稍后重试", firstViewModel.resultMessage.value)
        assertEquals(1, owner.clearCalls)

        composeRule.onNodeWithText("重新进入").performClick()
        composeRule.onNodeWithText("本机数据：23 条").assertIsDisplayed()
        composeRule.onNodeWithText("删除失败，请稍后重试").assertIsDisplayed()
        val reopenedViewModel = checkNotNull(activeViewModel)
        assertTrue(reopenedViewModel === firstViewModel)
        assertEquals(1, owner.clearCalls)
    }
}
