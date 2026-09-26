package com.xmu.course.ui.guide

import com.xmu.course.ui.tutorial.TutorialRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-11 / E1：详细指南的内容契约。
 *
 * 指南是对用户的承诺，这里守住四条：章节齐、跳转的分步教程真实存在、
 * 文案不出现“导入成绩/Excel/PDF”这类已经作废的说法，也不漏实现细节。
 */
class GuideContentTest {

    @Test
    fun `guide covers every bottom tab and the data chapter`() {
        assertEquals(
            listOf(
                GuideContent.SECTION_HOME,
                GuideContent.SECTION_TIMETABLE,
                GuideContent.SECTION_TODO,
                GuideContent.SECTION_ACADEMIC,
                GuideContent.SECTION_SIMULATION,
                GuideContent.SECTION_SETTINGS,
                GuideContent.SECTION_DATA,
            ),
            GuideContent.sections.map { it.id },
        )
    }

    @Test
    fun `every section can be found by id`() {
        GuideContent.sections.forEach { section ->
            assertEquals(section, GuideContent.find(section.id))
        }
        assertNull(GuideContent.find("no_such_section"))
        assertNull(GuideContent.find(null))
    }

    @Test
    fun `sections carry readable copy`() {
        GuideContent.sections.forEach { section ->
            assertTrue(section.id, section.title.isNotBlank())
            assertTrue(section.id, section.summary.isNotBlank())
            assertTrue(section.id, section.steps.isNotEmpty())
            assertTrue(section.id, section.tip.isNotBlank())
            section.allCopy().forEach { text -> assertTrue(text.isNotBlank()) }
        }
    }

    @Test
    fun `related tutorial ids all resolve in the registry`() {
        val related = GuideContent.sections.mapNotNull { it.relatedTutorialId }
        assertEquals(6, related.size)
        related.forEach { tutorialId ->
            assertTrue("未注册的分步教程 id: $tutorialId", TutorialRegistry.get(tutorialId) != null)
        }
        // 首页没有分步教程，指南不许凭空承诺。
        assertNull(GuideContent.find(GuideContent.SECTION_HOME)?.relatedTutorialId)
    }

    @Test
    fun `tutorial ids map back to their guide chapter`() {
        assertEquals(GuideContent.SECTION_TIMETABLE, GuideContent.sectionIdForTutorial("timetable"))
        assertEquals(GuideContent.SECTION_TODO, GuideContent.sectionIdForTutorial("todo"))
        assertEquals(GuideContent.SECTION_ACADEMIC, GuideContent.sectionIdForTutorial("academic_home"))
        assertEquals(GuideContent.SECTION_SIMULATION, GuideContent.sectionIdForTutorial("academic_simulation"))
        assertEquals(GuideContent.SECTION_SETTINGS, GuideContent.sectionIdForTutorial("settings"))
        assertEquals(GuideContent.SECTION_DATA, GuideContent.sectionIdForTutorial("data_management"))
        assertNull(GuideContent.sectionIdForTutorial("no_such_tutorial"))
    }

    @Test
    fun `academic chapters never promise an import flow`() {
        val forbidden = listOf("导入成绩", "Excel", "excel", "PDF", "pdf", "未导入")
        listOf(GuideContent.SECTION_ACADEMIC, GuideContent.SECTION_SIMULATION).forEach { id ->
            val section = requireNotNull(GuideContent.find(id))
            section.allCopy().forEach { text ->
                forbidden.forEach { word ->
                    assertTrue("${section.id} 含有过期说法「$word」：$text", !text.contains(word))
                }
            }
        }
    }

    @Test
    fun `academic chapter describes the manual refresh honestly`() {
        val academic = requireNotNull(GuideContent.find(GuideContent.SECTION_ACADEMIC))
        assertTrue(academic.allCopy().any { it.contains("刷新学业数据") })
        assertTrue(academic.allCopy().any { it.contains("不会后台自动访问教务系统") })
    }

    @Test
    fun `guide copy stays user facing`() {
        val jargon = listOf(
            "ViewModel", "Composable", "Modifier", "Retrofit", "OkHttp", "Room",
            "DAO", "SQLite", "Cookie", "JSESSIONID", "JSON", "HTTP", "Intent",
            "SharedPreferences", "NavHost", "API",
        )
        GuideContent.sections.forEach { section ->
            section.allCopy().forEach { text ->
                jargon.forEach { word ->
                    assertTrue("${section.id} 泄漏实现细节「$word」：$text", !text.contains(word))
                }
            }
        }
    }

    private fun GuideSection.allCopy(): List<String> = listOf(title, summary, tip) + steps
}
