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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学业模拟播种层（[AcademicSimulationSeedBuilder]）的纯函数测试。
 *
 * 全部使用合成数据（课程号/学分均为虚构），不含任何真实学业信息。
 * 覆盖 RC 热修：GPA 基线学分与展示 GPA 必须同源（countedCreditsText），
 * 使“仅刷新成绩、未刷新培养方案”的设备也能直接进入模拟；
 * 同时锁定本学期在修课程自动播种、按课程号去重、学分缺失绝不代猜、
 * 方案外三态策略不变等既有语义。
 */
class AcademicSimulationSeedTest {

    private fun entry(
        code: String,
        credits: String,
        point: String?,
        gradeText: String = "87",
        name: String = "历史课程",
        semester: String = "2024-2025-1",
    ) = JwGradeEntry(
        rowId = "R-$code",
        semesterCode = semester,
        semesterDisplay = "学期$semester",
        courseCode = code,
        courseName = name,
        creditsText = credits,
        gradeText = gradeText,
        pointGradeText = point,
        courseNatureDisplay = "必修",
        courseCategoryDisplay = null,
        offeringUnitDisplay = null,
        retakeCode = null,
    )

    private fun gradeSnapshot(vararg entries: JwGradeEntry) = JwGradeSnapshot(
        refreshedAtEpochMillis = 1_770_000_000_000L,
        providerId = "xmu.jw",
        sourceCapability = "cjcx.xscjcx",
        totalCreditsText = "9",
        entries = entries.toList(),
    )

    private fun course(
        code: String,
        name: String,
        credits: String?,
        status: SourceXfStatus = SourceXfStatus.CONFIRMED,
        inPlan: Boolean = true,
    ) = SourceCourse(
        courseCode = code,
        courseName = name,
        creditsText = credits,
        status = status,
        inPlan = inPlan,
        teacherNames = "合成教师",
        classCode = "CL-$code",
        confirmationHint = null,
    )

    private fun completion(
        label: String = "2026-2027-1",
        earned: String = "40",
        required: String = "160",
        enrolled: List<SourceCourse> = emptyList(),
        outsideCompleted: List<CompletedCourseOutsidePlan> = emptyList(),
    ) = AcademicCompletionSnapshot(
        schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
        semesterLabel = label,
        plan = PlanSummary("合成培养方案", required, earned, "2026-09-16 22:09", "3"),
        enrolledCourses = enrolled,
        completedCoursesOutsidePlan = outsideCompleted,
        localOverrides = emptyMap(),
    )

    // (16) RC 热修核心：仅成绩缓存、无培养方案快照时，基线学分复用 GPA 分母，不再报“缺学分”。
    @Test
    fun `仅成绩缓存无培养方案时基线学分复用GPA分母并可立即模拟`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = null,
            grade = gradeSnapshot(entry("C100", "3", "3.7"), entry("C101", "2", "2.0")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertEquals(SimulationGpaStatus.COMPUTED, seed.baseline?.gpaStatus)
        assertEquals("3.02", seed.baseline?.gpaText)
        // (3+2=5) 学分合计，与展示 GPA 同源；不再是 null（旧 bug 会报“已有成绩数据仍缺少学分信息”）。
        assertEquals("5", seed.baseline?.earnedCreditsText)
        assertTrue(seed.canSimulate)
        assertTrue(seed.availability is AcademicSimulationAvailability.Ready)
        assertNull(seed.availability.guidance)
        // 无培养方案 → 无在修课程可播种，交由页面 CASE B 文案说明。
        assertTrue(seed.currentSemesterCourses.isEmpty())
    }

    // (16) 基线学分与 GPA 同源，即使培养方案给出更大的“已获学分”也不改用其污染投影。
    @Test
    fun `基线学分采用GPA分母而非方案已获学分`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = completion(
                earned = "40",
                enrolled = listOf(course("C200", "在修甲", "2")),
            ),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertEquals("3.70", seed.baseline?.gpaText)
        assertEquals("3", seed.baseline?.earnedCreditsText)
        // (6) 本学期在修 C200 的 2 学分绝不并入历史已修基线（否则会变成 5）。
    }

    // (1)(2)(4)(5) 本学期在修课程自动播种、带可信学分、按课程号去重、已结课只入基线不作滑块。
    @Test
    fun `本学期在修自动播种且与历史成绩按课程号去重`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = completion(
                enrolled = listOf(
                    course("C200", "在修甲", "2"),
                    course("C300", "在修乙", null, SourceXfStatus.NEEDS_MANUAL),
                    course("C100", "历史甲", "3"),
                ),
            ),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertEquals(listOf("C200"), seed.simlatableCourses.map { it.courseCode })
        assertEquals(listOf("C300"), seed.unresolvedCourses.map { it.courseCode })
        assertEquals(listOf("C100"), seed.alreadyGradedCourses.map { it.courseCode })
        assertEquals("C100", seed.alreadyGradedCourses.single().realGradeText?.let { "C100" })
        assertEquals(3, seed.currentSemesterCourses.size) // 一门课一行，无重复
        assertEquals("2", seed.simlatableCourses.single().creditsText) // (2) 学分来自可信来源
    }

    // (3) 多门有效课程全部各播种一次。
    @Test
    fun `多门有效在修课程全部各播种一次`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = completion(
                enrolled = listOf(
                    course("C200", "甲", "2"),
                    course("C400", "乙", "1"),
                    course("C500", "丙", "3"),
                ),
            ),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertEquals(listOf("C200", "C400", "C500"), seed.simlatableCourses.map { it.courseCode })
        assertEquals(listOf("2", "1", "3"), seed.simlatableCourses.map { it.creditsText })
        assertEquals(3, seed.currentSemesterCourses.count { !it.alreadyGraded })
        assertTrue(seed.canSimulate)
    }

    // (7) 学分缺失课程标记待确认，绝不代猜。
    @Test
    fun `学分缺失课程标记待确认且绝不猜测`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = completion(
                enrolled = listOf(course("C300", "在修乙", null, SourceXfStatus.NEEDS_MANUAL)),
            ),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertTrue(seed.simlatableCourses.isEmpty())
        val unresolved = seed.unresolvedCourses.single()
        assertEquals("C300", unresolved.courseCode)
        assertTrue(unresolved.needsCreditConfirmation)
        assertNull(unresolved.creditsText)
    }

    // (8) 一门缺学分不影响其它有效课程参与。
    @Test
    fun `一门缺学分不影响其它有效课程参与`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = completion(
                enrolled = listOf(
                    course("C200", "甲", "2"),
                    course("C400", "乙", "1"),
                    course("C300", "丙", null, SourceXfStatus.NEEDS_MANUAL),
                ),
            ),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertEquals(listOf("C200", "C400"), seed.simlatableCourses.map { it.courseCode })
        assertEquals(1, seed.unresolvedCourses.size)
    }

    // (12) 在修集合变化时播种跟随培养方案（本学期口径）。
    @Test
    fun `在修课程集合变化时播种跟随培养方案`() {
        val a = AcademicSimulationSeedBuilder.build(
            completion = completion(enrolled = listOf(course("C200", "甲", "2"))),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        val b = AcademicSimulationSeedBuilder.build(
            completion = completion(label = "2026-2027-2", enrolled = listOf(course("C500", "戊", "1"))),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertEquals("C200", a.simlatableCourses.single().courseCode)
        assertEquals("C500", b.simlatableCourses.single().courseCode)
        assertEquals("2026-2027-2", b.semesterLabel)
    }

    // (13) 成绩完整但无在修课程：基线可用、可模拟，交由页面显示 CASE B。
    @Test
    fun `成绩完整但无在修课程时基线仍可用`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = completion(enrolled = emptyList()),
            grade = gradeSnapshot(entry("C100", "3", "3.7")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertTrue(seed.canSimulate)
        assertEquals(0, seed.currentSemesterCourses.size)
    }

    // (14) 无任何缓存：空态。
    @Test
    fun `无任何缓存时判定为空态`() {
        val seed = AcademicSimulationSeedBuilder.build(null, null, OutsidePlanGpaPolicy.UNCONFIRMED)
        assertFalse(seed.sourceAvailable)
        assertTrue(seed.availability is AcademicSimulationAvailability.Empty)
        assertEquals("尚未同步学业数据。", seed.availability.guidance)
    }

    // (15) 方案外三态策略语义保持不变；仅 COMPUTED 时基线学分才复用 GPA 分母。
    @Test
    fun `方案外三态策略语义不受基线学分改动影响`() {
        val comp = completion(
            earned = "40",
            enrolled = listOf(course("C100", "甲", "3", inPlan = false)),
        )
        val grade = gradeSnapshot(entry("C100", "3", "3.7"))

        val unconfirmed = AcademicSimulationSeedBuilder.build(comp, grade, OutsidePlanGpaPolicy.UNCONFIRMED)
        assertEquals(SimulationGpaStatus.NEEDS_CONFIRMATION, unconfirmed.baseline?.gpaStatus)
        assertFalse(unconfirmed.canSimulate)

        val include = AcademicSimulationSeedBuilder.build(comp, grade, OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN)
        assertEquals(SimulationGpaStatus.COMPUTED, include.baseline?.gpaStatus)
        assertEquals("3", include.baseline?.earnedCreditsText)
        assertTrue(include.canSimulate)

        val exclude = AcademicSimulationSeedBuilder.build(comp, grade, OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN)
        assertEquals(SimulationGpaStatus.INSUFFICIENT, exclude.baseline?.gpaStatus)
        assertNull(exclude.baseline?.gpaText)
        // 非 COMPUTED 时回退培养方案已获学分（既有行为，未擅改）。
        assertEquals("40", exclude.baseline?.earnedCreditsText)
        assertFalse(exclude.canSimulate)
    }

    // (5) 纯历史结课（无在修）只进基线，不作为可模拟滑块。
    @Test
    fun `纯历史结课只进基线不生成滑块`() {
        val seed = AcademicSimulationSeedBuilder.build(
            completion = null,
            grade = gradeSnapshot(entry("C100", "3", "3.7"), entry("C101", "2", "4.0")),
            policy = OutsidePlanGpaPolicy.UNCONFIRMED,
        )
        assertTrue(seed.simlatableCourses.isEmpty())
        assertTrue(seed.alreadyGradedCourses.isEmpty()) // 无在修记录 → 无 graded 行
        assertTrue(seed.canSimulate)
        assertEquals("5", seed.baseline?.earnedCreditsText)
    }
}
