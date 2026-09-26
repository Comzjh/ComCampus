package com.xmu.course.ui.grades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学业模拟 VM 测试：以手动基线覆盖旧的 Excel 导入装配。
 * 真实教务缓存 seeding 的等价覆盖见 AcademicSimulationSeedTest 与本文件的 seed 合并用例。
 */
class GradesSandboxViewModelTest {
    @Test
    fun `初始状态为空且不包含规划结果`() {
        val viewModel = GradesSandboxViewModel()

        assertTrue(viewModel.uiState.value.courses.isEmpty())
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `添加课程后可以计算加权预计GPA`() {
        val viewModel = GradesSandboxViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        viewModel.addCourse()
        val first = viewModel.uiState.value.courses.single().id
        viewModel.updateCourseName(first, "高等数学")
        viewModel.updateCourseCredits(first, "3")
        viewModel.updateCourseGrade(first, "A")
        viewModel.addCourse()
        val second = viewModel.uiState.value.courses.last().id
        viewModel.updateCourseName(second, "大学物理")
        viewModel.updateCourseCredits(second, "1")
        viewModel.updateCourseGrade(second, "B")

        viewModel.simulate()

        assertEquals(3.75, viewModel.uiState.value.result?.gpa ?: -1.0, 0.0001)
        assertEquals(4.0, viewModel.uiState.value.result?.totalCredits ?: -1.0, 0.0001)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `百分制成绩使用60到100分滑块范围`() {
        val viewModel = GradesSandboxViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        viewModel.addCourse()
        val courseId = viewModel.uiState.value.courses.single().id
        viewModel.updateCourseName(courseId, "课程")
        viewModel.updateCourseCredits(courseId, "1")

        viewModel.updateCourseScore(courseId, 100)
        viewModel.simulate()
        assertEquals(4.0, viewModel.uiState.value.result?.gpa ?: -1.0, 0.0001)

        viewModel.updateCourseScore(courseId, 60)
        viewModel.simulate()
        assertEquals(1.0, viewModel.uiState.value.result?.gpa ?: -1.0, 0.0001)
        assertEquals(60, viewModel.uiState.value.courses.single().score)
    }

    @Test
    fun `填写目标GPA后显示目标差距`() {
        val viewModel = GradesSandboxViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("3")
        viewModel.updateManualCompletedCredits("1")
        viewModel.updateTargetGpa("3.5")
        viewModel.addCourse()
        val courseId = viewModel.uiState.value.courses.single().id
        viewModel.updateCourseName(courseId, "课程")
        viewModel.updateCourseCredits(courseId, "1")
        viewModel.updateCourseGrade(courseId, "A")

        viewModel.simulate()

        assertEquals(3.5, viewModel.uiState.value.result?.gpa ?: -1.0, 0.0001)
        assertEquals(0.0, viewModel.uiState.value.result?.targetGap ?: -1.0, 0.0001)
    }

    @Test
    fun `删除课程后重新计算前结果被清除`() {
        val viewModel = GradesSandboxViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("0")
        viewModel.updateManualCompletedCredits("0")
        viewModel.addCourse()
        val courseId = viewModel.uiState.value.courses.single().id
        viewModel.updateCourseName(courseId, "课程")
        viewModel.updateCourseCredits(courseId, "3")
        viewModel.updateCourseGrade(courseId, "A")
        viewModel.simulate()

        viewModel.removeCourse(courseId)

        assertTrue(viewModel.uiState.value.courses.isEmpty())
        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `非法输入显示错误且不产生结果`() {
        val viewModel = GradesSandboxViewModel()
        viewModel.setManualBaselineEnabled(true)
        viewModel.updateManualCurrentGpa("not-a-number")
        viewModel.updateManualCompletedCredits("0")
        viewModel.addCourse()
        val courseId = viewModel.uiState.value.courses.single().id
        viewModel.updateCourseName(courseId, "课程")
        viewModel.updateCourseCredits(courseId, "3")
        viewModel.updateCourseGrade(courseId, "A")

        viewModel.simulate()

        assertNull(viewModel.uiState.value.result)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("当前 GPA") == true)
    }
}
