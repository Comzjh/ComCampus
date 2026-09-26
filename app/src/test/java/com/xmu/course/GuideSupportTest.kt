package com.xmu.course

import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.ui.AppRoutes
import com.xmu.course.ui.AppDestination
import com.xmu.course.ui.guide.GuideContent
import com.xmu.course.ui.guide.UserGuideScreen
import com.xmu.course.ui.support.SupportScreen
import com.xmu.course.ui.support.PROJECT_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuideSupportTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guideNavigationTest() {
        assertEquals("user_guide", AppRoutes.USER_GUIDE)
        assertTrue(AppDestination.entries.none { it.route == AppRoutes.USER_GUIDE && it.label == "教程" })
        assertTrue(AppDestination.entries.none { it.route == AppRoutes.TRONCLASS })
        assertEquals("tronclass", AppRoutes.TRONCLASS)
        assertEquals("jw_auth", AppRoutes.JW_AUTH)
        assertTrue(AppRoutes.JW_AUTH != AppRoutes.IMPORT)
        assertEquals("academic_history", AppRoutes.ACADEMIC_HISTORY)
        assertTrue(AppDestination.entries.none { it.route == "grades_transcript" || it.route == "academic_import" })
        assertTrue(AppDestination.Grades.childRoutes.none { it == "grades_transcript" || it == "academic_import" })
        assertTrue(AppDestination.Grades.childRoutes.contains(AppRoutes.ACADEMIC_HISTORY))
        composeRule.setContent { MaterialTheme { UserGuideScreen(onBack = {}) } }
        composeRule.onNodeWithText("使用教程").assertIsDisplayed()
    }

    @Test
    fun guideScrollTest() {
        // 指南按章节渲染：滚到底必须能看到最后一章「数据管理」，
        // 不再使用已经作废的分区名「数据安全」。
        composeRule.setContent { MaterialTheme { UserGuideScreen(onBack = {}) } }
        var reached = false
        var attempts = 0
        while (!reached && attempts < 14) {
            val chapter = composeRule.onAllNodesWithTag("guide_section_" + GuideContent.SECTION_DATA)
                .fetchSemanticsNodes()
            reached = chapter.isNotEmpty()
            if (!reached) {
                composeRule.onNodeWithTag("guide_list").performTouchInput { swipeUp() }
                composeRule.waitForIdle()
                composeRule.mainClock.advanceTimeBy(400L)
            }
            attempts++
        }
        assertTrue("滚到底也找不到最后一章", reached)
        composeRule.onNodeWithText("数据安全").assertDoesNotExist()
    }

    @Test
    fun supportNavigationTest() {
        assertEquals("support", AppRoutes.SUPPORT)
        assertEquals("https://github.com/Comzjh/ComCampus", PROJECT_URL)
        composeRule.setContent { MaterialTheme { SupportScreen(onBack = {}) } }
        composeRule.onNodeWithText("支持 ComCampus").assertIsDisplayed()
    }

    @Test
    fun qrResourceLoadTest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resources = listOf(R.drawable.alipay_qr, R.drawable.wechat_qr)
        resources.forEach { id ->
            val bitmap = context.resources.openRawResource(id).use(BitmapFactory::decodeStream)
            assertNotNull(bitmap)
            assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
        }
    }
}
