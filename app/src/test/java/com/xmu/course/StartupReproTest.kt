package com.xmu.course

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 冷启动复现测试（JVM，无需真机/模拟器）：
 * MainActivity → WelcomeScreen → 点击"开始导入课表" → 主界面组合（含 Room 打开）。
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
        composeRule.onNodeWithText("欢迎使用 XMU Course").assertExists()

        // 2. 点击"开始导入课表" → 进入主界面（触发 TimetableScreen + ViewModel + Room）
        composeRule.onNodeWithText("开始导入课表").performClick()
        composeRule.waitForIdle()

        // 3. 主界面出现（底部导航"课表"标签）
        composeRule.onAllNodesWithText("课表")[0].assertIsDisplayed()
    }

}
