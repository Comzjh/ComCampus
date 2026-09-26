package com.xmu.course.ui.academic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG-05 回归：“有没有数据”“有没有学分”“有没有可用绩点”“能不能模拟”
 * 必须是同一套口径，不能再出现学业页有数据、模拟页说没数据。
 */
class AcademicSimulationAvailabilityTest {

    private fun seed(
        sourceAvailable: Boolean,
        gpaText: String?,
        status: SimulationGpaStatus,
        earnedCredits: String?,
    ): AcademicSimulationSeed = AcademicSimulationSeed(
        sourceAvailable = sourceAvailable,
        semesterLabel = "2026-2027-1",
        baseline = if (!sourceAvailable) {
            null
        } else {
            SimulationBaseline(
                gpaText = gpaText,
                gpaStatus = status,
                earnedCreditsText = earnedCredits,
                requiredCreditsText = "160",
                planName = "合成培养方案",
                outsidePlanCompletedCount = 0,
                pendingCreditCourseCount = 0,
                dataUpdatedText = null,
            )
        },
        currentSemesterCourses = emptyList(),
    )

    @Test
    fun `学分与绩点齐备时判定为可模拟且不给提示`() {
        val availability = seed(true, "3.50", SimulationGpaStatus.COMPUTED, "40").availability
        assertTrue(availability is AcademicSimulationAvailability.Ready)
        assertTrue(availability.canSimulate)
        assertNull(availability.guidance)
    }

    @Test
    fun `只有学分时只说明缺成绩`() {
        listOf(SimulationGpaStatus.INSUFFICIENT, SimulationGpaStatus.NO_DATA).forEach { status ->
            val availability = seed(true, null, status, "40").availability
            assertEquals(
                AcademicSimulationAvailability.Partial(
                    creditsAvailable = true,
                    gradesAvailable = false,
                ),
                availability,
            )
            assertEquals(
                "已有学分进度，暂无可用于 GPA 计算的成绩。",
                availability.guidance,
            )
            assertFalse(availability.canSimulate)
        }
    }

    @Test
    fun `待确认口径属于部分数据而不是没数据`() {
        val availability = seed(
            true,
            "3.50",
            SimulationGpaStatus.NEEDS_CONFIRMATION,
            "40",
        ).availability
        assertTrue(availability is AcademicSimulationAvailability.Partial)
        assertTrue((availability as AcademicSimulationAvailability.Partial).creditsAvailable)
        assertFalse(availability.guidance.orEmpty().contains("还没有"))
    }

    @Test
    fun `只有绩点时只说明缺学分`() {
        val availability = seed(true, "3.50", SimulationGpaStatus.COMPUTED, null).availability
        assertEquals(
            "已有成绩数据，仍缺少计算所需的学分信息。",
            availability.guidance,
        )
    }

    @Test
    fun `两样都缺时明确列出两个缺口`() {
        val availability = seed(true, null, SimulationGpaStatus.NO_DATA, null).availability
        assertEquals(
            "学业数据还不完整：学分进度与可用 GPA 都还没同步到。",
            availability.guidance,
        )
    }

    @Test
    fun `本机无快照才算空态`() {
        val availability = seed(false, null, SimulationGpaStatus.NO_DATA, null).availability
        assertTrue(availability is AcademicSimulationAvailability.Empty)
        assertEquals("尚未同步学业数据。", availability.guidance)
    }

    @Test
    fun `所有提示不再出现导入Excel与PDF叙事`() {
        listOf(
            seed(true, "3.50", SimulationGpaStatus.COMPUTED, "40"),
            seed(true, null, SimulationGpaStatus.INSUFFICIENT, "40"),
            seed(true, "3.50", SimulationGpaStatus.COMPUTED, null),
            seed(true, null, SimulationGpaStatus.NO_DATA, null),
            seed(false, null, SimulationGpaStatus.NO_DATA, null),
        ).forEach { s ->
            val text = s.availability.guidance.orEmpty()
            listOf("导入", "Excel", "PDF", "未导入").forEach { banned ->
                assertFalse("guidance must not mention " + banned, text.contains(banned))
            }
        }
    }
}
