package com.xmu.course.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the distinction between primary refresh actions and contextual navigation. */
class RcVisualWeightContractTest {

    private val repoRoot: File
        get() {
            var dir = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
            repeat(4) {
                if (File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()) {
                    return dir
                }
                dir = checkNotNull(dir.parentFile)
            }
            error("project root not found")
        }

    private fun read(vararg parts: String): String =
        File(repoRoot, "app/src/main/java/com/xmu/course/" + parts.joinToString("/"))
            .readText(Charsets.UTF_8)
            .replace("\r\n", "\n")

    @Test
    fun `manual refresh cta matches the timetable import cta weight`() {
        val dataSources = read("ui", "academic", "AcademicDataSourcesScreen.kt")
        val refreshCta = dataSources.substringAfter("onClick = onRefresh,")
            .substringBefore("Text(")
        assertTrue("manual refresh must stay a filled Button", dataSources.contains("Button("))
        assertTrue("manual refresh must preserve the 48dp minimum touch target", refreshCta.contains(".heightIn(min = 48.dp)"))
        assertTrue(
            "spacing must sit outside the flexible button height",
            refreshCta.indexOf(".padding(top = AppSpacing.Sm)") < refreshCta.indexOf(".heightIn(min = 48.dp)"),
        )
    }

    @Test
    fun `timetable import opens through a grouped row and saves from the ready page toolbar`() {
        val import = read("ui", "import", "ImportScreen.kt")
        val rows = read("ui", "components", "NavigationRow.kt")
        val webView = read("ui", "import", "WebViewScreen.kt")

        assertTrue(import.contains("AppGroupedSection(title = \"在线导入\")"))
        assertTrue(import.contains("title = \"打开厦大教务\""))
        assertTrue(rows.contains(".heightIn(min = 52.dp)"))
        assertTrue(webView.contains("WebViewTimetableSaveAction("))
        assertTrue(webView.contains("isVisible = hasCheckedCurrentPage"))
        assertTrue(webView.contains("isTimetableReady = canSaveTimetable"))
        assertTrue(webView.contains(".sizeIn(minWidth = 48.dp, minHeight = 48.dp)"))
        assertTrue(webView.contains("isTimetableHtml(html)"))
        assertEquals(0, Regex("floatingActionButton\\s*=").findAll(webView).count())
    }

    @Test
    fun `manual refresh copy stays user triggered and never says import`() {
        val dataSources = read("ui", "academic", "AcademicDataSourcesScreen.kt")
        assertTrue(dataSources.contains("刷新学业数据"))
        listOf("导入成绩", "Excel", "PDF").forEach { banned ->
            assertTrue("unexpected $banned in data sources screen", !dataSources.contains(banned))
        }
    }

    @Test
    fun `confirmed local data deletion keeps destructive action in error color`() {
        val providerData = read("ui", "providermanagement", "ProviderDataManagementScreen.kt")
        assertTrue(
            "final delete confirmation must retain destructive error styling",
            providerData.contains("contentColor = MaterialTheme.colorScheme.error"),
        )
    }

    @Test
    fun `authentication status labels occupy their own row outside official web content`() {
        val jwAuth = read("ui", "auth", "JwAuthScreen.kt")
        assertTrue(jwAuth.contains(".background(MaterialTheme.colorScheme.surfaceVariant)"))
        assertTrue(jwAuth.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertTrue(jwAuth.indexOf("text = statusText") < jwAuth.indexOf("AndroidView("))

        val tronAuth = read("ui", "tronclass", "auth", "TronClassAuthActivity.kt")
        assertTrue(tronAuth.contains("val root = LinearLayout(this)"))
        assertTrue(tronAuth.contains("val webContent = FrameLayout(this)"))
        assertTrue(
            tronAuth.indexOf("root.addView(\n            statusView") <
                tronAuth.indexOf("root.addView(\n            webContent"),
        )
    }
}
