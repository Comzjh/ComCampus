package com.xmu.course.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.data.auth.AuthStatus
import com.xmu.course.data.auth.AuthStatusSource
import com.xmu.course.data.auth.WiseduAuthObservation
import com.xmu.course.data.auth.WiseduAuthStatusController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 认证中心视觉状态测试：全部使用合成状态，不触碰真实认证链路。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthCenterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class StaticSource(private val value: AuthStatus) : AuthStatusSource {
        override suspend fun check(): AuthStatus = value
    }

    private class PendingSource : AuthStatusSource {
        override suspend fun check(): AuthStatus = suspendCancellableCoroutine { }
    }

    private class PendingWisedu : WiseduAuthStatusController {
        override suspend fun check(): AuthStatus = suspendCancellableCoroutine { }
        override fun applyObservation(observation: WiseduAuthObservation): AuthStatus = AuthStatus.UNKNOWN
        override fun clear() = Unit
    }

    private class FakeWisedu(private val value: AuthStatus) : WiseduAuthStatusController {
        override suspend fun check(): AuthStatus = value
        override fun applyObservation(observation: WiseduAuthObservation): AuthStatus = value
        override fun clear() = Unit
    }

    private fun show(
        wisedu: AuthStatus,
        tron: AuthStatus,
        wiseduLogoutSupported: Boolean = true,
        pending: Boolean = false,
    ) {
        val tronSource: AuthStatusSource = if (pending) PendingSource() else StaticSource(tron)
        val wiseduSource: WiseduAuthStatusController = if (pending) PendingWisedu() else FakeWisedu(wisedu)
        composeRule.setContent {
            MaterialTheme {
                AuthCenterScreen(
                    viewModel = AuthCenterViewModel(wiseduSource, tronSource),
                    onOpenWisedu = {},
                    onLogoutWisedu = {},
                    wiseduLogoutSupported = wiseduLogoutSupported,
                    onOpenTronClass = {},
                    onLogoutTronClass = {},
                )
            }
        }
    }

    @Test
    fun authenticatedRendersSuccessChipsAndHidesRefreshingRow() {
        show(AuthStatus.AUTHENTICATED, AuthStatus.AUTHENTICATED)
        composeRule.onAllNodesWithText("已认证").assertCountEquals(2)
        composeRule.onNodeWithTag("auth_refreshing").assertDoesNotExist()
        composeRule.onNodeWithText("打开教务").assertIsDisplayed()
        composeRule.onNodeWithText("管理畅课").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun authRequiredRendersWarningChip() {
        show(AuthStatus.AUTH_REQUIRED, AuthStatus.AUTHENTICATED)
        composeRule.onNodeWithText("需要登录").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已认证").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pendingCheckShowsCheckingChipsAndLoadingRow() {
        show(AuthStatus.CHECKING, AuthStatus.CHECKING, pending = true)
        composeRule.onAllNodesWithText("检查中").assertCountEquals(2)
        composeRule.onNodeWithTag("auth_refreshing").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("正在检查认证状态…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun expiredRendersWarningChipWithGuidance() {
        show(AuthStatus.AUTHENTICATED, AuthStatus.EXPIRED)
        composeRule.onNodeWithText("已过期").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("登录已过期，重新打开页面验证即可恢复。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun unknownWithoutLogoutSupportShowsNeutralChipAndDisabledLabel() {
        show(AuthStatus.UNKNOWN, AuthStatus.AUTHENTICATED, wiseduLogoutSupported = false)
        composeRule.onNodeWithText("状态未知").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("状态未知，请打开页面完成一次验证。")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("退出登录（待验证）").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun internalEnumNamesAreNotDisplayed() {
        show(AuthStatus.AUTHENTICATED, AuthStatus.AUTH_REQUIRED)
        composeRule.onAllNodesWithText("AUTHENTICATED").assertCountEquals(0)
        composeRule.onAllNodesWithText("AUTH_REQUIRED").assertCountEquals(0)
    }

    @Test
    fun dualActionsStayInside360DpAtLargeFontScale() {
        val wiseduSource = FakeWisedu(AuthStatus.UNKNOWN)
        val tronSource = StaticSource(AuthStatus.AUTHENTICATED)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.3f),
            ) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        AuthCenterScreen(
                            viewModel = AuthCenterViewModel(wiseduSource, tronSource),
                            onOpenWisedu = {},
                            onLogoutWisedu = {},
                            wiseduLogoutSupported = false,
                            onOpenTronClass = {},
                            onLogoutTronClass = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        val openButton = composeRule.onNodeWithTag("auth_wisedu_open_button")
        val logoutButton = composeRule.onNodeWithTag("auth_wisedu_logout_button")
        openButton.assertIsDisplayed()
        logoutButton.assertIsDisplayed()
        val openBounds = openButton.fetchSemanticsNode().boundsInRoot
        val logoutBounds = logoutButton.fetchSemanticsNode().boundsInRoot
        val widthPx = with(composeRule.density) { 360.dp.toPx() }
        val minTargetHeightPx = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("Open action must remain within the narrow viewport", openBounds.left >= 0f && openBounds.right <= widthPx)
        assertTrue("Logout action must remain within the narrow viewport", logoutBounds.left >= 0f && logoutBounds.right <= widthPx)
        assertTrue("Open action must keep a 48dp touch target", openBounds.height >= minTargetHeightPx)
        assertTrue("Logout action must keep a 48dp touch target", logoutBounds.height >= minTargetHeightPx)
        assertTrue("Actions should stack vertically at large font scale", logoutBounds.top >= openBounds.bottom)
    }
}
