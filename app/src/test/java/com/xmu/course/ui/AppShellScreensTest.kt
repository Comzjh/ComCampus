package com.xmu.course.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.ui.profile.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShellScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileScreenShowsEntriesAndRoutes() {
        val clicks = mutableMapOf(
            "settings" to 0,
            "auth" to 0,
            "guide" to 0,
            "support" to 0,
            "provider_management" to 0,
            "data_management" to 0,
            "tronclass" to 0,
        )
        composeRule.setContent {
            MaterialTheme {
                ProfileScreen(
                    onOpenSettings = { clicks["settings"] = clicks["settings"]!! + 1 },
                    onOpenAuthCenter = { clicks["auth"] = clicks["auth"]!! + 1 },
                    onOpenGuide = { clicks["guide"] = clicks["guide"]!! + 1 },
                    onOpenSupport = { clicks["support"] = clicks["support"]!! + 1 },
                    onOpenProviderManagement = { clicks["provider_management"] = clicks["provider_management"]!! + 1 },
                    onOpenDataManagement = { clicks["data_management"] = clicks["data_management"]!! + 1 },
                    onOpenTronClass = { clicks["tronclass"] = clicks["tronclass"]!! + 1 },
                )
            }
        }
composeRule.onNodeWithText("个人中心").assertIsDisplayed()
        composeRule.onNodeWithText("小工具").assertDoesNotExist()
        composeRule.onNodeWithText("登录与数据来源").assertExists()
        composeRule.onNodeWithText("数据与隐私").assertExists()
        composeRule.onNodeWithText("外观与启动").assertExists()
        composeRule.onNodeWithText("关于").assertExists()
        val tags = listOf(
            "profile_auth_center",
            "profile_tronclass",
            "profile_provider_management",
            "profile_data_management",
            "profile_default_startup",
            "profile_guide",
            "profile_support",
        )
        tags.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        assertEquals(
            mapOf(
                "settings" to 1,
                "auth" to 1,
                "guide" to 1,
                "support" to 1,
                "provider_management" to 1,
                "data_management" to 1,
                "tronclass" to 1,
            ),
            clicks,
        )
    }
}
