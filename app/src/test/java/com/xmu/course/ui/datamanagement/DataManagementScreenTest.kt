package com.xmu.course.ui.datamanagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xmu.course.contracts.provider.PrivacyDataOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeOwner : PrivacyDataOwner {
        override suspend fun countLocalData(): Int = 0
        override suspend fun clearLocalData() = Unit
    }

    private val sources = listOf(
        DataManagementSource("xmu.wisedu", "课表数据", "手动导入的学期、课程与课表", FakeOwner()),
        DataManagementSource("xmu.academic_import", "成绩记录", "手动导入的成绩文件数据", FakeOwner()),
    )

    @Test
    fun listShowsAllSourcesAndReportsTap() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                DataManagementScreen(
                    sources = sources,
                    onOpenSource = opened::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("本机数据").assertIsDisplayed()
        composeRule.onNodeWithText("课表数据").assertIsDisplayed()
        composeRule.onNodeWithText("成绩记录").assertIsDisplayed()
        composeRule.onNodeWithTag("data_source_xmu.wisedu").performClick()
        composeRule.onNodeWithTag("data_source_xmu.academic_import").performClick()
        assertEquals(listOf("xmu.wisedu", "xmu.academic_import"), opened)
    }

    @Test
    fun listDoesNotDeleteAnythingByItself() {
        composeRule.setContent {
            MaterialTheme {
                DataManagementScreen(sources = sources, onOpenSource = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("删除只影响本机数据，不会退出登录，也不会自动重新同步。")
            .assertIsDisplayed()
    }
}
