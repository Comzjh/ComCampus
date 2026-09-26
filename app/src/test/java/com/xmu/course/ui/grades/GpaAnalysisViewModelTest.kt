package com.xmu.course.ui.grades

import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.contracts.grades.TranscriptSemester
import com.xmu.course.contracts.grades.TranscriptSnapshot
import com.xmu.course.contracts.grades.TranscriptSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GpaAnalysisViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun 无快照映射为Unavailable() = runTest {
        val viewModel = GpaAnalysisViewModel(FakeReader(null))
        advanceUntilIdle()
        assertEquals(GpaAnalysisState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun reader抛异常映射为Unavailable() = runTest {
        val viewModel = GpaAnalysisViewModel(ThrowingReader)
        advanceUntilIdle()
        assertEquals(GpaAnalysisState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun 空课程快照映射为NoCourses() = runTest {
        val viewModel = GpaAnalysisViewModel(FakeReader(snapshotOf()))
        advanceUntilIdle()
        assertEquals(GpaAnalysisState.NoCourses, viewModel.uiState.value)
    }

    @Test
    fun 全部不可换算映射为Insufficient() = runTest {
        val viewModel = GpaAnalysisViewModel(
            FakeReader(snapshotOf(TranscriptCourse("美术", null, 2.0))),
        )
        advanceUntilIdle()
        assertEquals(GpaAnalysisState.Insufficient, viewModel.uiState.value)
    }

    @Test
    fun 有效快照映射为Ready并给出计算结果() = runTest {
        val viewModel = GpaAnalysisViewModel(
            FakeReader(
                snapshotOf(
                    TranscriptCourse("数学", "90", 4.0, source = TranscriptSource.ACADEMIC_IMPORT),
                    TranscriptCourse("程序", "A", 2.0, source = TranscriptSource.MANUAL),
                ),
            ),
        )
        advanceUntilIdle()
        val state = viewModel.uiState.value as GpaAnalysisState.Ready
        val analysis = state.analysis
        assertEquals(4.0, analysis.gpa, 1e-9)
        assertEquals(6.0, analysis.totalCredits, 1e-9)
        assertEquals(2, analysis.includedCourses)
        assertEquals(0, analysis.excludedCourses)
        assertTrue(analysis.gpa.isFinite())
    }

    private fun snapshotOf(vararg courses: TranscriptCourse) = TranscriptSnapshot(
        semester = TranscriptSemester("2026 春季学期", "20262"),
        courses = courses.toList(),
    )

    private class FakeReader(
        private val snapshot: TranscriptSnapshot?,
    ) : TranscriptReader {
        override suspend fun getTranscript(): TranscriptSnapshot? = snapshot
    }

    private object ThrowingReader : TranscriptReader {
        override suspend fun getTranscript(): TranscriptSnapshot = error("reader failed")
    }
}
