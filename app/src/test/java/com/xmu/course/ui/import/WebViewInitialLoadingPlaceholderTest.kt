package com.xmu.course.ui.import

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewInitialLoadingPlaceholderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholderExplainsTheInitialLoadAndDisappearsAfterVisiblePageCommit() {
        val isVisible = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    WebViewInitialLoadingPlaceholder(isVisible = isVisible.value)
                }
            }
        }

        composeRule.onNodeWithTag("webview_initial_loading").assertIsDisplayed()
        composeRule.onNodeWithText("正在加载教务页面").assertIsDisplayed()

        composeRule.runOnIdle { isVisible.value = false }

        composeRule.onNodeWithTag("webview_initial_loading").assertDoesNotExist()
    }

    @Test
    fun initialMainFrameFailureOffersOnlyUserTriggeredRetry() {
        val isVisible = mutableStateOf(true)
        val hasError = mutableStateOf(true)
        var retryCount = 0
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    WebViewInitialLoadingPlaceholder(
                        isVisible = isVisible.value,
                        hasError = hasError.value,
                        onRetry = {
                            retryCount += 1
                            hasError.value = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("教务页面加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").assertIsDisplayed()
        assertEquals(0, retryCount)

        composeRule.onNodeWithTag("webview_initial_retry").performClick()
        composeRule.waitForIdle()

        assertEquals(1, retryCount)
        composeRule.onNodeWithText("正在加载教务页面").assertIsDisplayed()
    }
}
