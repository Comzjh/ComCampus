package com.xmu.course

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 冷启动复现测试（JVM，无需真机/模拟器）：
 * MainActivity → WelcomeScreen → 首页入口 → 启动页面选择 → 主界面组合（含 Room 打开）。
 * 任何启动期运行时崩溃都会在本测试中以完整堆栈抛出。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupReproTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun `冷启动到主界面不崩溃`() {
        // 1. 欢迎页渲染
        composeRule.waitForIdle()
        composeRule.onNodeWithText("欢迎使用 ComCampus").assertExists()

        // 2. 从欢迎页完成初始入口选择 → 进入主界面（触发 TimetableScreen + ViewModel + Room）
        composeRule.onNodeWithTag("welcome_start").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("startup_choice_home").performClick()
        composeRule.onNodeWithTag("startup_choice_continue").performClick()
        composeRule.waitForIdle()

        // 3. 主界面出现（底部导航"首页"标签）
        composeRule.onNodeWithText("首页").assertIsDisplayed()
    }

}
