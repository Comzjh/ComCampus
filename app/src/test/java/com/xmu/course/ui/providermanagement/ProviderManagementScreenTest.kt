package com.xmu.course.ui.providermanagement

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.xmu.course.di.FlowAuthStateSource
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.ProviderManagementEntry
import com.xmu.course.contracts.provider.SyncPolicy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun entries(): List<ProviderManagementEntry> = listOf(
        ProviderManagementEntry(
            descriptor = ProviderDescriptor(
                "xmu.wisedu",
                setOf(ProviderCapability.TIMETABLE),
                SyncPolicy.MANUAL_ONLY,
            ),
            statusSource = FlowAuthStateSource { com.xmu.course.contracts.provider.AuthState.AUTHENTICATED },
        ),
        ProviderManagementEntry(
            descriptor = ProviderDescriptor(
                "xmu.tronclass",
                setOf(ProviderCapability.TODO),
                SyncPolicy.FOREGROUND_ALLOWED,
            ),
            statusSource = FlowAuthStateSource { com.xmu.course.contracts.provider.AuthState.AUTH_REQUIRED },
        ),
        ProviderManagementEntry(
            descriptor = ProviderDescriptor(
                "xmu.jw",
                setOf(ProviderCapability.CAMPUS_SERVICE),
                SyncPolicy.MANUAL_ONLY,
            ),
            statusSource = FlowAuthStateSource { com.xmu.course.contracts.provider.AuthState.AUTHENTICATED },
        ),
    )

    @Test
    fun providerCardsDisplayChineseTitles() {
        composeRule.setContent {
            ProviderManagementScreen(
                viewModel = ProviderManagementViewModel(entries()),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("金智教务").assertIsDisplayed()
        composeRule.onNodeWithText("畅课").assertIsDisplayed()
        composeRule.onNodeWithText("厦大教务").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun providerCardsDisplayCapabilityAndSyncPolicy() {
        composeRule.setContent {
            ProviderManagementScreen(
                viewModel = ProviderManagementViewModel(entries()),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("能力: 课表").assertIsDisplayed()
        composeRule.onNodeWithText("能力: 待办").assertIsDisplayed()
        composeRule.onNodeWithText("能力: 校园服务").performScrollTo().assertIsDisplayed()
        // 「同步: 手动」同时出现在 Wisedu 与 JW 卡片上，需按多个节点断言
        composeRule.onAllNodesWithText("同步: 手动")[0].assertIsDisplayed()
        composeRule.onNodeWithText("同步: 前台刷新").assertIsDisplayed()
    }
@Test
    fun missingStatusSourceHidesStatusRow() {
        composeRule.setContent {
            ProviderManagementScreen(
                viewModel = ProviderManagementViewModel(
                    listOf(
                        ProviderManagementEntry(
                            ProviderDescriptor(
                                "xmu.wisedu",
                                setOf(ProviderCapability.TIMETABLE),
                                SyncPolicy.MANUAL_ONLY,
                            ),
                        ),
                    ),
                ),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("金智教务").assertIsDisplayed()
        composeRule.onAllNodesWithText("状态: 已配置").fetchSemanticsNodes().let {
            assertTrue(it.isEmpty())
        }
    }

    @Test
    fun authStatusRenderedAsChipLabels() {
        composeRule.setContent {
            ProviderManagementScreen(
                viewModel = ProviderManagementViewModel(entries()),
                onBack = {},
            )
        }

        // Wisedu 与 JW 均为 AUTHENTICATED，TronClass 为 AUTH_REQUIRED。
        composeRule.onAllNodesWithText("已配置")[0].assertIsDisplayed()
        composeRule.onNodeWithText("需要登录").performScrollTo().assertIsDisplayed()
    }
}
