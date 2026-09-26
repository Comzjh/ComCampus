package com.xmu.course.ui.grades

import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.contracts.grades.TranscriptSource
import com.xmu.course.domain.grades.GpaSandboxScales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯计算审计：加权 GPA、排除规则、换算兜底、来源无关与顺序无关。 */
class TranscriptGpaAnalyzerTest {

    private val scale = GpaSandboxScales.commonLetter

    private fun course(
        name: String,
        score: String?,
        credits: Double?,
        gradeText: String? = null,
        source: TranscriptSource? = null,
    ) = TranscriptCourse(
        courseName = name,
        score = score,
        credits = credits,
        gradeText = gradeText,
        source = source,
    )

    @Test
    fun 无课程返回Empty() {
        val analysis = TranscriptGpaAnalyzer.analyze(emptyList(), scale)
        assertEquals(TranscriptGpaAnalysis.Empty, analysis)
    }

    @Test
    fun 百分制与等级制混合按学分加权() {
        val analysis = TranscriptGpaAnalyzer.analyze(
            listOf(
                course("数学", "90", 4.0),
                course("物理", "B+", 2.0),
            ),
            scale,
        )
        val computed = analysis as TranscriptGpaAnalysis.Computed
        assertEquals((4.0 * 4.0 + 3.3 * 2.0) / 6.0, computed.gpa, 1e-9)
        assertEquals(6.0, computed.totalCredits, 1e-9)
        assertEquals(2, computed.includedCourses)
        assertEquals(0, computed.excludedCourses)
    }

    @Test
    fun 全部无法换算返回Insufficient() {
        val analysis = TranscriptGpaAnalyzer.analyze(
            listOf(
                course("美术", null, 2.0),
                course("历史", "优", 1.0),
                course("体育", "55", 1.0),
            ),
            scale,
        )
        assertEquals(TranscriptGpaAnalysis.Insufficient, analysis)
    }

    @Test
    fun 学分缺失或非正数的课程被排除且不中断() {
        val analysis = TranscriptGpaAnalyzer.analyze(
            listOf(
                course("数学", "90", null),
                course("物理", "85", 0.0),
                course("程序", "A", -1.0),
                course("英语", "88", 3.0),
            ),
            scale,
        )
        val computed = analysis as TranscriptGpaAnalysis.Computed
        assertEquals(3.7, computed.gpa, 1e-9)
        assertEquals(3.0, computed.totalCredits, 1e-9)
        assertEquals(1, computed.includedCourses)
        assertEquals(3, computed.excludedCourses)
    }

    @Test
    fun score缺失时回退gradeText且不做推断() {
        val analysis = TranscriptGpaAnalyzer.analyze(
            listOf(course("化学", null, 2.0, gradeText = "A")),
            scale,
        )
        val computed = analysis as TranscriptGpaAnalysis.Computed
        assertEquals(4.0, computed.gpa, 1e-9)
        assertEquals(0, computed.excludedCourses)
    }

    @Test
    fun 非数值成绩按等级原文交给规则换算不猜测() {
        val analysis = TranscriptGpaAnalyzer.analyze(
            listOf(course("政治", "A-", 2.0)),
            scale,
        )
        val computed = analysis as TranscriptGpaAnalysis.Computed
        assertEquals(3.7, computed.gpa, 1e-9)
    }

    @Test
    fun 来源与顺序不影响结果() {
        val courses = listOf(
            course("数学", "90", 4.0, source = TranscriptSource.ACADEMIC_IMPORT),
            course("物理", "B+", 2.0, source = TranscriptSource.MANUAL),
            course("程序", "85", 3.0, source = TranscriptSource.OFFICIAL_TRANSCRIPT),
        )
        val forward = TranscriptGpaAnalyzer.analyze(courses, scale) as TranscriptGpaAnalysis.Computed
        val backward = TranscriptGpaAnalyzer.analyze(courses.reversed(), scale) as TranscriptGpaAnalysis.Computed
        assertEquals(forward.gpa, backward.gpa, 1e-9)
        assertEquals(forward.totalCredits, backward.totalCredits, 1e-9)
        assertEquals(3, forward.includedCourses)
        assertTrue(forward.gpa.isFinite())
    }
}
