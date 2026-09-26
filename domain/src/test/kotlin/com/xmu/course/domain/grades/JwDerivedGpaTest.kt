package com.xmu.course.domain.grades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地派生 GPA 语义：官方绩点加权、合格制 N/A、字段异常透明计数、
 * 方案外三态策略（未确认不出数），全部使用十进制字符串口径。
 */
class JwDerivedGpaTest {

    private fun course(credits: String, points: String?, outside: Boolean = false) =
        JwDerivedGpa.Course(creditsText = credits, pointGradeText = points, outsidePlan = outside)

    @Test
    fun officialPointsAreWeightedByCredits() {
        val result = JwDerivedGpa.compute(
            listOf(course("4", "4.0"), course("2", "3.0")),
            OutsidePlanGpaPolicy.UNCONFIRMED,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("3.67", result.gpaText)
        assertEquals("6", result.countedCreditsText)
        assertEquals(2, result.countedCourses)
    }

    @Test
    fun passFailCoursesCountCreditsButExcludedFromGpa() {
        val result = JwDerivedGpa.compute(
            listOf(course("3", "N/A"), course("2", "3.5"), course("1", "n/a")),
            OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("3.50", result.gpaText)
        assertEquals(1, result.countedCourses)
        assertEquals(2, result.pointFreeCourses)
    }

    @Test
    fun malformedFieldsAreCountedNotGuessed() {
        val result = JwDerivedGpa.compute(
            listOf(
                course("2", "4.0"),
                course("abc", "4.0"),
                course("2", ""),
                course("2", null),
                course("0", "3.0"),
            ),
            OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN,
        ) as JwDerivedGpa.Result.Computed
        assertEquals(4, result.malformedCourses)
        assertEquals(1, result.countedCourses)
        assertEquals("4.00", result.gpaText)
        assertTrue(
            JwDerivedGpa.compute(
                listOf(course("abc", "4.0"), course("2", "")),
                OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN,
            ) is JwDerivedGpa.Result.Insufficient,
        )
    }

    @Test
    fun outsidePlanUnconfirmedBlocksAnyNumber() {
        val result = JwDerivedGpa.compute(
            listOf(course("4", "4.0"), course("2", "3.0", outside = true)),
            OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertTrue(result is JwDerivedGpa.Result.NeedsConfirmation)
        assertEquals(1, (result as JwDerivedGpa.Result.NeedsConfirmation).candidateCount)
    }

    @Test
    fun outsidePlanIncludeAddsCandidateToComputation() {
        val result = JwDerivedGpa.compute(
            listOf(course("2", "4.0"), course("2", "2.0", outside = true)),
            OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("3.00", result.gpaText)
        assertEquals(2, result.countedCourses)
        assertEquals(0, result.excludedByPolicy)
    }

    @Test
    fun outsidePlanExcludeRemovesCandidateOnly() {
        val result = JwDerivedGpa.compute(
            listOf(course("2", "4.0"), course("2", "2.0", outside = true)),
            OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("4.00", result.gpaText)
        assertEquals(1, result.countedCourses)
        assertEquals(1, result.excludedByPolicy)
    }

    @Test
    fun excludePolicyWithOnlyCandidatesIsInsufficient() {
        val result = JwDerivedGpa.compute(
            listOf(course("2", "2.0", outside = true)),
            OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN,
        )
        assertTrue(result is JwDerivedGpa.Result.Insufficient)
    }

    @Test
    fun noCandidatesNeverPromptsUnderUnconfirmed() {
        val result = JwDerivedGpa.compute(
            listOf(course("2", "3.0"), course("3", "4.0")),
            OutsidePlanGpaPolicy.UNCONFIRMED,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("3.60", result.gpaText)
    }

    @Test
    fun roundingIsHalfUpOnOfficialScaleTwo() {
        val result = JwDerivedGpa.compute(
            listOf(course("1", "3.335")),
            OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("3.34", result.gpaText)
    }

    @Test
    fun zeroPointGradeIsStillValid() {
        val result = JwDerivedGpa.compute(
            listOf(course("2", "0"), course("2", "4.0")),
            OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN,
        ) as JwDerivedGpa.Result.Computed
        assertEquals("2.00", result.gpaText)
        assertEquals(2, result.countedCourses)
    }
}
