package com.xmu.course.domain.grades

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GpaCalculatorTest {
    private val scale = GradePointScale(
        letterPoints = mapOf("A" to 4.0, "A-" to 3.7, "B" to 3.0),
        percentageBands = listOf(
            PercentageBand(90.0, 100.0, 4.0),
            PercentageBand(85.0, 89.0, 3.7),
            PercentageBand(80.0, 84.0, 3.0),
        ),
    )

    @Test
    fun `calculates weighted gpa from baseline and simulated courses`() {
        val result = GpaCalculator.simulate(
            baseline = GpaBaseline(gpa = 3.0, credits = 20.0),
            courses = listOf(
                SimulatedCourse("课程 A", 4.0, GradeInput.Percentage(90.0)),
            ),
            scale = scale,
        )

        assertEquals(3.1666666667, result.gpa, absoluteTolerance = 0.0000000001)
        assertEquals(24.0, result.totalCredits)
        assertEquals(1, result.simulatedCourseCount)
    }

    @Test
    fun `supports multiple courses and letter grades`() {
        val result = GpaCalculator.simulate(
            baseline = GpaBaseline(gpa = 2.0, credits = 10.0),
            courses = listOf(
                SimulatedCourse("课程 A", 3.0, GradeInput.Letter(" a- ")),
                SimulatedCourse("课程 B", 2.0, GradeInput.Letter("B")),
            ),
            scale = scale,
        )

        assertEquals((20.0 + 3.0 * 3.7 + 2.0 * 3.0) / 15.0, result.gpa)
        assertEquals(15.0, result.totalCredits)
    }

    @Test
    fun `uses inclusive percentage boundaries`() {
        assertEquals(4.0, scale.pointsFor(GradeInput.Percentage(90.0)))
        assertEquals(4.0, scale.pointsFor(GradeInput.Percentage(100.0)))
        assertEquals(3.7, scale.pointsFor(GradeInput.Percentage(85.0)))
        assertEquals(3.7, scale.pointsFor(GradeInput.Percentage(89.0)))
    }

    @Test
    fun `reports target gap as target minus simulated gpa`() {
        val result = GpaCalculator.simulate(
            baseline = GpaBaseline(gpa = 3.0, credits = 20.0),
            courses = listOf(SimulatedCourse("课程 A", 4.0, GradeInput.Letter("A"))),
            scale = scale,
            targetGpa = 3.5,
        )

        assertEquals(3.1666666667, result.gpa, absoluteTolerance = 0.0000000001)
        assertEquals(0.3333333333, result.targetGap!!, absoluteTolerance = 0.0000000001)
    }

    @Test
    fun `empty simulation keeps current gpa`() {
        val result = GpaCalculator.simulate(
            baseline = GpaBaseline(gpa = 3.25, credits = 32.0),
            courses = emptyList(),
            scale = scale,
        )

        assertEquals(3.25, result.gpa)
        assertEquals(32.0, result.totalCredits)
        assertEquals(0, result.simulatedCourseCount)
    }

    @Test
    fun `rejects invalid credits and grades`() {
        assertFailsWith<IllegalArgumentException> {
            SimulatedCourse("课程 A", 0.0, GradeInput.Letter("A"))
        }
        assertFailsWith<IllegalArgumentException> {
            GpaBaseline(gpa = 3.0, credits = -1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            scale.pointsFor(GradeInput.Percentage(84.5))
        }
        assertFailsWith<IllegalArgumentException> {
            scale.pointsFor(GradeInput.Letter("C"))
        }
    }

    @Test
    fun `rejects overlapping percentage rules`() {
        assertFailsWith<IllegalArgumentException> {
            GradePointScale(
                letterPoints = emptyMap(),
                percentageBands = listOf(
                    PercentageBand(90.0, 100.0, 4.0),
                    PercentageBand(85.0, 90.0, 3.7),
                ),
            )
        }
    }
}
