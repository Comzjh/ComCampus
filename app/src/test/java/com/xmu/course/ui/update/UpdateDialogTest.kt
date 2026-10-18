package com.xmu.course.ui.update

import android.app.Application
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.update.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateDialogTest {
    @get:Rule val rule = createComposeRule()

    private val update = UpdateCheckResult.Available(
        currentVersion = "0.9.9", latestVersion = "0.9.10",
        releaseUrl = "https://github.com/Comzjh/ComCampus/releases/tag/v0.9.10",
        apkUrl = "https://github.com/Comzjh/ComCampus/releases/download/v0.9.10/ComCampus-0.9.10.apk",
        apkDigest = "sha256:${"a".repeat(64)}",
        releaseNotes = "## 更新内容\n- 修复课表顶栏\n- 翘课课程显示灰色",
    )

    @Test
    fun summaryDetailsAndDownloadActionAreAvailableTogether() {
        var downloads = 0
        rule.setContent {
            MaterialTheme {
                UpdateDialog(update, {}, false, null, false, { downloads++ })
            }
        }
        rule.onNodeWithText("• 修复课表顶栏\n• 翘课课程显示灰色").assertExists()
        assertEquals(0, downloads)
        rule.onNodeWithText("GitHub 详情").performClick()
        val application = ApplicationProvider.getApplicationContext<Application>()
        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(update.releaseUrl, intent.dataString)
        assertEquals(0, downloads)
        rule.onNodeWithText("下载并安装").performClick()
        assertEquals(1, downloads)
    }

    @Test
    fun downloadingDisablesDuplicateDownloadButRetainsDetailsAndDismiss() {
        var dismissed = false
        rule.setContent {
            MaterialTheme {
                UpdateDialog(update, { dismissed = true }, true, null, false, {})
            }
        }
        rule.onNodeWithText("正在下载…").assertIsNotEnabled()
        rule.onNodeWithText("GitHub 详情").assertExists()
        rule.onNodeWithText("稍后").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun blankNotesAndMissingAssetStillOfferGitHubDetails() {
        rule.setContent {
            MaterialTheme {
                UpdateDialog(update.copy(releaseNotes = "", apkUrl = null, apkDigest = null),
                    {}, false, null, true, {})
            }
        }
        rule.onNodeWithText("暂无更新说明，可查看 GitHub 详情。").assertExists()
        rule.onNodeWithText("GitHub 详情").assertExists()
        rule.onNodeWithText("前往 GitHub").assertExists()
    }

    @Test
    fun summariesArePlainTextAndBounded() {
        assertEquals("• 修复课表\n• 查看说明\n• 调整按钮", summarizeReleaseNotes(
            "## 更新内容\n- **修复课表**\n2. [查看说明](https://example.invalid)\n+ 调整`按钮`\n- 第四条",
        ))
        val long = summarizeReleaseNotes("- " + "更新".repeat(200))
        assertTrue(long.length <= 180)
        assertTrue(long.endsWith("…"))
        assertEquals("暂无更新说明，可查看 GitHub 详情。", summarizeReleaseNotes("\n## 更新内容\n---\n"))
    }
}
