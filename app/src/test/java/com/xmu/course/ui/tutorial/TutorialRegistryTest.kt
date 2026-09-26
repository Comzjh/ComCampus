package com.xmu.course.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialRegistryTest {

    @Test
    fun registryContainsEightPageTutorials() {
        assertEquals(
            listOf(
                "academic_home",
                "academic_simulation",
                "academic_plan",
                "academic_data_sources",
                "timetable",
                "todo",
                "settings",
                "data_management",
            ),
            TutorialRegistry.ids(),
        )
    }

    @Test
    fun everyTutorialHasTwoToSixStepsWithNonBlankCopy() {
        TutorialRegistry.ids().forEach { id ->
            val definition = requireNotNull(TutorialRegistry.get(id))
            assertTrue("tutorial $id must have 2-6 steps", definition.steps.size in 2..6)
            definition.steps.forEach { step ->
                // D5：targetKey 允许为 null（有意为之的引导步），但绝不允许空字符串。
                assertTrue(
                    "step target must be null or non-blank in $id",
                    step.targetKey == null || step.targetKey.isNotBlank(),
                )
                assertTrue("step title blank in $id", step.title.isNotBlank())
                assertTrue("step message blank in $id", step.message.isNotBlank())
            }
        }
    }

    @Test
    fun tutorialCopyNeverUsesAcademicImportWords() {
        // UX-04/UX-05：学业教程只能教“刷新”，不能把已废弃的导入叙事带回来。
        TutorialRegistry.ids().forEach { id ->
            TutorialRegistry.get(id)!!.steps.forEach { step ->
                val text = step.title + step.message
                listOf("导入成绩", "未导入", "Excel", "PDF").forEach { banned ->
                    assertTrue("tutorial $id must not mention $banned", !text.contains(banned))
                }
            }
        }
    }

    @Test
    fun academicHomeOpensWithATargetlessIntroStep() {
        // BUG-09 / D4：首步不能锚定「学业概览」——新用户首次进入时那张卡片根本不存在。
        val steps = requireNotNull(TutorialRegistry.get("academic_home")).steps
        assertNull(steps.first().targetKey)
        assertEquals("学业", steps.first().title)
        assertTrue(steps.drop(1).all { it.targetKey != null })
    }

    @Test
    fun unknownPageHasNoTutorial() {
        assertNull(TutorialRegistry.get("nonexistent_page"))
    }

    @Test
    fun seenKeyEncodesIdAndVersion() {
        val definition = requireNotNull(TutorialRegistry.get("academic_home"))
        assertEquals("tutorial_seen_academic_home_v1", definition.seenKey)
        val bumped = definition.copy(version = 2)
        assertEquals("tutorial_seen_academic_home_v2", bumped.seenKey)
    }
}
