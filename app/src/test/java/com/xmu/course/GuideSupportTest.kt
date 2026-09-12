package com.xmu.course

import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.ui.AppRoutes
import com.xmu.course.ui.AppDestination
import com.xmu.course.ui.guide.UserGuideScreen
import com.xmu.course.ui.support.SupportScreen
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
        assertTrue(AppDestination.entries.any { it.route == AppRoutes.USER_GUIDE && it.label == "教程" })
        composeRule.setContent { MaterialTheme { UserGuideScreen(onBack = {}) } }
        composeRule.onNodeWithText("使用教程").assertIsDisplayed()
    }

    @Test
    fun guideScrollTest() {
        composeRule.setContent { MaterialTheme { UserGuideScreen(onBack = {}) } }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onNodeWithText("数据安全").assertIsDisplayed()
    }

    @Test
    fun supportNavigationTest() {
        assertEquals("support", AppRoutes.SUPPORT)
        composeRule.setContent { MaterialTheme { SupportScreen(onBack = {}) } }
        composeRule.onNodeWithText("支持 XMU Course").assertIsDisplayed()
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
