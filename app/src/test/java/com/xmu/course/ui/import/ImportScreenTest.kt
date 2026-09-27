package com.xmu.course.ui.import

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importOptionsKeepOnlineAndLocalActionsInGroupedRows() {
        var webViewOpens = 0
        var guideOpens = 0
        var htmlPickerOpens = 0

        composeRule.setContent {
            MaterialTheme {
                ImportOptionsContent(
                    onOpenWebView = { webViewOpens += 1 },
                    onOpenGuide = { guideOpens += 1 },
                    onPickHtml = { htmlPickerOpens += 1 },
                )
            }
        }

        composeRule.onNodeWithText("在线导入").assertIsDisplayed()
        composeRule.onNodeWithText("本地导入").assertIsDisplayed()

        listOf("import_open_webview", "import_open_guide", "import_pick_html").forEach { tag ->
            val row = composeRule.onNodeWithTag(tag)
            row.assertIsDisplayed().assertHasClickAction()
            val height = with(composeRule.density) {
                row.fetchSemanticsNode().boundsInRoot.height.toDp()
            }
            assertTrue("$tag must keep a comfortable row target", height >= 52.dp)
        }

        composeRule.onNodeWithTag("import_open_webview").performClick()
        composeRule.onNodeWithTag("import_open_guide").performClick()
        composeRule.onNodeWithTag("import_pick_html").performClick()

        assertEquals(1, webViewOpens)
        assertEquals(1, guideOpens)
        assertEquals(1, htmlPickerOpens)
    }

    @Test
    @OptIn(ExperimentalMaterial3Api::class)
    fun startDatePickerUsesChineseLabelsAndTheRequestedTeachingMonth() {
        val september2026 = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()
        composeRule.setContent {
            MaterialTheme {
                ProvideSimplifiedChineseDatePickerLocale {
                    val datePickerState = remember {
                        DatePickerState(
                            locale = java.util.Locale.SIMPLIFIED_CHINESE,
                            initialDisplayedMonthMillis = september2026,
                        )
                    }
                    ImportStartDatePickerBody(
                        datePickerState = datePickerState,
                        onOpenAcademicCalendar = {},
                    )
                }
            }
        }

        val semanticsTree = composeRule.onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .let(::describeSemanticsTree)
        assertTrue(semanticsTree, semanticsTree.contains("选定的日期"))
        assertTrue(semanticsTree, semanticsTree.contains("选第一教学周周一"))
        assertTrue(semanticsTree, semanticsTree.contains("2026年9月"))
        assertTrue(semanticsTree, !semanticsTree.contains("Select date"))
        assertTrue(semanticsTree, !semanticsTree.contains("September 2026"))
    }

    @Test
    fun startDatePickerOpensOfficialCalendarAndDefaultsToSeptemberSeventh2026() {
        var openedUrl: String? = null
        var confirmedDate: Long? = null

        composeRule.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        openedUrl = uri
                    }
                },
            ) {
                MaterialTheme {
                    ImportStartDatePickerDialog(
                        onDismissRequest = {},
                        onConfirm = { confirmedDate = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("厦大校历（2026–2027）")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(XMU_ACADEMIC_CALENDAR_URL, openedUrl)

        composeRule.onNodeWithText("2026年9月7日").assertIsDisplayed()
        composeRule.onNodeWithText("确认导入").performClick()
        assertEquals(Instant.parse("2026-09-07T00:00:00Z").toEpochMilli(), confirmedDate)
    }

    private fun describeSemanticsTree(node: SemanticsNode): String =
        buildString {
            append(node.config)
            node.children.forEach { child -> append('\n').append(describeSemanticsTree(child)) }
        }
}
