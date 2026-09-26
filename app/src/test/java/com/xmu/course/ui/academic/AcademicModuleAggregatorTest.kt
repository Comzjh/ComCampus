package com.xmu.course.ui.academic

import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.CompletedCourseOutsidePlan
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学业聚合层纯函数测试（全部合成数据，零 Android 依赖）。
 *
 * 锁定：口径不互相补齐、来源事实不被派生计算修改、
 * 方案外课程在 UNCONFIRMED 下绝不出数、官方文本原样保留。
 */
class AcademicModuleAggregatorTest {

    private fun course(
        code: String,
        name: String = "合成课程$code",
        credits: String? = "3",
        status: SourceXfStatus = SourceXfStatus.CONFIRMED,
        inPlan: Boolean = true,
    ) = SourceCourse(
        courseCode = code,
        courseName = name,
        creditsText = credits,
        status = status,
        inPlan = inPlan,
        teacherNames = "合成教师",
        classCode = "CL01",
        confirmationHint = null,
    )

    private fun planSnapshot(
        required: String = "160",
        earned: String = "40",
        enrolled: List<SourceCourse> = listOf(course("C001")),
        outsideCompleted: List<CompletedCourseOutsidePlan> = emptyList(),
    ) = AcademicCompletionSnapshot(
        schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
        semesterLabel = "2026-2027-1",
        plan = PlanSummary(
            planName = "合成培养方案",
            requiredCreditsText = required,
            earnedCreditsText = earned,
            sourceSnapshotAt = "2026-09-16 22:09",
            sourceThisSemesterTotalText = "3",
        ),
        enrolledCourses = enrolled,
        completedCoursesOutsidePlan = outsideCompleted,
        localOverrides = emptyMap(),
    )

    private fun grade(
        entries: List<JwGradeEntry>,
        totalCredits: String = "6",
    ) = JwGradeSnapshot(
        refreshedAtEpochMillis = 1_770_000_000_000L,
        providerId = "xmu.jw",
        sourceCapability = "cjcx.xscjcx",
        totalCreditsText = totalCredits,
        entries = entries,
    )

    private fun gradeEntry(
        code: String,
        semester: String = "2025-2026-1",
        semesterDisplay: String = "2025-2026学年第一学期",
        credits: String = "3",
        points: String? = "3.7",
        gradeText: String = "87",
        name: String = "历史课程$code",
    ) = JwGradeEntry(
        rowId = "R$code$semester",
        semesterCode = semester,
        semesterDisplay = semesterDisplay,
        courseCode = code,
        courseName = name,
        creditsText = credits,
        gradeText = gradeText,
        pointGradeText = points,
        courseNatureDisplay = "必修",
        courseCategoryDisplay = null,
        offeringUnitDisplay = null,
        retakeCode = null,
    )

    // ---------- 方案外归属 ----------

    @Test
    fun outsidePlanCodesUnionCompletedAndEnrolled() {
        val snapshot = planSnapshot(
            enrolled = listOf(
                course("C001"),
                course("X001", inPlan = false, credits = null, status = SourceXfStatus.NEEDS_MANUAL),
            ),
            outsideCompleted = listOf(
                CompletedCourseOutsidePlan("Y001", "方案外结课", "2", "2024-2025-2", "80"),
            ),
        )
        assertEquals(setOf("X001", "Y001"), AcademicModuleAggregator.outsidePlanCourseCodes(snapshot))
    }

    @Test
    fun outsidePlanCodesEmptyWithoutSnapshot() {
        assertTrue(AcademicModuleAggregator.outsidePlanCourseCodes(null).isEmpty())
    }

    // ---------- 概览口径 ----------

    @Test
    fun overviewNullWithoutPlanSnapshot() {
        assertNull(AcademicModuleAggregator.overview(null))
    }

    @Test
    fun overviewDerivesRemainingAndProgressFromPlanOnly() {
        val row = AcademicModuleAggregator.overview(planSnapshot())!!
        assertEquals("160", row.requiredCreditsText)
        assertEquals("40", row.earnedCreditsText)
        assertEquals("120", row.remainingCreditsText)
        assertEquals(0.25f, row.progressFraction!!, 0.0001f)
        assertEquals(1, row.enrolledCount)
        assertEquals(0, row.pendingManualCount)
    }

    @Test
    fun overviewNeverShowsNegativeRemaining() {
        val row = AcademicModuleAggregator.overview(planSnapshot(required = "40", earned = "57"))!!
        assertNull(row.remainingCreditsText)
        assertEquals(1f, row.progressFraction!!, 0.0001f)
    }

    @Test
    fun overviewKeepsSourceDiscrepancyInsteadOfMerging() {
        // 官方两源口径不同（培养方案 55 / 成绩记录合计 57）时，各自原样呈现，绝不互相补齐。
        val row = AcademicModuleAggregator.overview(planSnapshot(required = "160", earned = "55"))!!
        val grades = grade(totalCredits = "57", entries = listOf(gradeEntry("C001")))
        val history = AcademicModuleAggregator.historySemesters(grades, emptySet())
        assertEquals("55", row.earnedCreditsText)
        assertEquals("57", grades.totalCreditsText)
        assertEquals(1, history.size)
    }

    @Test
    fun overviewLeavesNumbersNullWhenSourceUnparsable() {
        val row = AcademicModuleAggregator.overview(planSnapshot(required = "待定", earned = "not-a-number"))!!
        assertNull(row.remainingCreditsText)
        assertNull(row.progressFraction)
    }

    // ---------- 历史成绩 ----------

    @Test
    fun historyEmptyWithoutGradeSnapshot() {
        assertTrue(AcademicModuleAggregator.historySemesters(null, emptySet()).isEmpty())
    }

    @Test
    fun historyGroupsBySemesterPreservingOfficialText() {
        val semesters = AcademicModuleAggregator.historySemesters(
            grade(
                entries = listOf(
                    gradeEntry("C001", credits = "3", points = "3.7", gradeText = "87"),
                    gradeEntry("C002", credits = "2", points = "4.0", gradeText = "92"),
                    gradeEntry(
                        "C003",
                        semester = "2025-2026-2",
                        semesterDisplay = "2025-2026学年第二学期",
                        credits = "1.5",
                        points = "N/A",
                        gradeText = "合格",
                    ),
                ),
            ),
            emptySet(),
        )
        assertEquals(2, semesters.size)
        val first = semesters.first { it.row.semesterCode == "2025-2026-1" }
        assertEquals(2, first.row.courseCount)
        assertEquals("5", first.row.creditsText)
        assertEquals("3.82", first.row.officialPointsAverageText)
        // 官方成绩文本原样保留，不做数值改写。
        assertEquals("87", first.courses.first { it.courseName == "历史课程C001" }.gradeText)
        val second = semesters.first { it.row.semesterCode == "2025-2026-2" }
        assertNull(second.row.officialPointsAverageText)
        assertEquals("合格", second.courses.single().gradeText)
        assertEquals("1.5", second.courses.single().creditsText)
    }

    @Test
    fun historyMarksOutsidePlanRowsFromPlanAttributionOnly() {
        val outside = AcademicModuleAggregator.outsidePlanCourseCodes(
            planSnapshot(
                enrolled = listOf(course("C002", inPlan = false)),
            ),
        )
        val semesters = AcademicModuleAggregator.historySemesters(
            grade(entries = listOf(gradeEntry("C001"), gradeEntry("C002"))),
            outside,
        )
        val row = semesters.single()
        assertTrue(row.row.hasOutsidePlanCourse)
        assertTrue(row.courses.first { it.courseName == "历史课程C002" }.outsidePlan)
        assertTrue(!row.courses.first { it.courseName == "历史课程C001" }.outsidePlan)
    }

    @Test
    fun historyFallsBackToCodeWhenDisplayBlank() {
        val semesters = AcademicModuleAggregator.historySemesters(
            grade(entries = listOf(gradeEntry("C001", semesterDisplay = "  "))),
            emptySet(),
        )
        assertEquals("2025-2026-1", semesters.single().row.semesterDisplay)
    }

    // ---------- GPA 策略与派生 ----------

    @Test
    fun gpaNoDataWhenGradeCacheMissing() {
        val summary = AcademicModuleAggregator.gpaSummary(
            grade = null,
            outsideCodes = emptySet(),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
            planSynced = true,
        )
        assertEquals(AcademicGpaTone.NO_DATA, summary.tone)
        assertNull(summary.valueText)
    }

    @Test
    fun gpaNeedsConfirmationBlocksNumberWhenCandidateExists() {
        val outside = setOf("C002")
        val summary = AcademicModuleAggregator.gpaSummary(
            grade = grade(entries = listOf(gradeEntry("C001"), gradeEntry("C002"))),
            outsideCodes = outside,
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
            planSynced = true,
        )
        assertEquals(AcademicGpaTone.NEEDS_CONFIRMATION, summary.tone)
        assertNull(summary.valueText)
        assertEquals(1, summary.candidateCount)
    }

    @Test
    fun gpaIncludeAndExcludePoliciesDifferOnlyInDerivedValue() {
        val grade = grade(entries = listOf(gradeEntry("C001"), gradeEntry("C002", points = "2.0")))
        val outside = setOf("C002")
        val included = AcademicModuleAggregator.gpaSummary(
            grade, outside, OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN, planSynced = true,
        )
        val excluded = AcademicModuleAggregator.gpaSummary(
            grade, outside, OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN, planSynced = true,
        )
        assertEquals(AcademicGpaTone.COMPUTED, included.tone)
        assertEquals(AcademicGpaTone.COMPUTED, excluded.tone)
        assertEquals(2, included.countedCourses)
        assertEquals(1, excluded.countedCourses)
        assertEquals(1, excluded.excludedByPolicy)
        assertTrue(included.valueText != excluded.valueText)
        // 官方记录文本与学分完全不受策略影响。
        assertEquals("87", grade.entries.first().gradeText)
        assertEquals("3", grade.entries.first().creditsText)
        assertEquals("3.7", grade.entries.first().pointGradeText)
        assertEquals(2, grade.entries.size)
    }

    @Test
    fun gpaInsufficientWhenNoUsablePoints() {
        val summary = AcademicModuleAggregator.gpaSummary(
            grade = grade(entries = listOf(gradeEntry("C001", points = null))),
            outsideCodes = emptySet(),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
            planSynced = true,
        )
        assertEquals(AcademicGpaTone.INSUFFICIENT, summary.tone)
        assertNull(summary.valueText)
    }

    @Test
    fun gpaFlagsPlanNotSyncedInsteadOfGuessingAttribution() {
        val summary = AcademicModuleAggregator.gpaSummary(
            grade = grade(entries = listOf(gradeEntry("C001"))),
            outsideCodes = emptySet(),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
            planSynced = false,
        )
        assertTrue(!summary.planSynced)
        assertEquals(AcademicGpaTone.COMPUTED, summary.tone)
    }

    @Test
    fun noOutsidePlanNeverPromptsForConfirmation() {
        val summary = AcademicModuleAggregator.gpaSummary(
            grade = grade(entries = listOf(gradeEntry("C001"))),
            outsideCodes = emptySet(),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
            planSynced = true,
        )
        assertEquals(AcademicGpaTone.COMPUTED, summary.tone)
        assertEquals(0, summary.candidateCount)
    }

    @Test
    fun aggregationIsDeterministicAcrossRepeatedCalls() {
        val snapshot = planSnapshot()
        val grade = grade(entries = listOf(gradeEntry("C001"), gradeEntry("C002")))
        val codes = AcademicModuleAggregator.outsidePlanCourseCodes(snapshot)
        val first = AcademicModuleAggregator.gpaSummary(grade, codes, OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN, true)
        val second = AcademicModuleAggregator.gpaSummary(grade, codes, OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN, true)
        assertEquals(first, second)
        assertEquals(
            AcademicModuleAggregator.historySemesters(grade, codes),
            AcademicModuleAggregator.historySemesters(grade, codes),
        )
    }
}
