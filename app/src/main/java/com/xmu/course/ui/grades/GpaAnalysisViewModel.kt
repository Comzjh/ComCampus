package com.xmu.course.ui.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.domain.grades.GpaSandboxScales
import com.xmu.course.domain.grades.GradePointScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * GPA 分析的 ViewModel：从只读 TranscriptReader 取课程并做纯计算。
 *
 * 计算结果（Calculated Data）只存在于内存状态，绝不写回 TranscriptCourse、
 * AcademicRecord，也不做任何持久化。
 */
class GpaAnalysisViewModel(
    private val reader: TranscriptReader,
    private val scale: GradePointScale = GpaSandboxScales.commonLetter,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GpaAnalysisState>(GpaAnalysisState.Loading)
    val uiState: StateFlow<GpaAnalysisState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val snapshot = runCatching { reader.getTranscript() }.getOrNull()
            if (snapshot == null) {
                _uiState.value = GpaAnalysisState.Unavailable
                return@launch
            }
            _uiState.value = when (val analysis = TranscriptGpaAnalyzer.analyze(snapshot.courses, scale)) {
                TranscriptGpaAnalysis.Empty -> GpaAnalysisState.NoCourses
                TranscriptGpaAnalysis.Insufficient -> GpaAnalysisState.Insufficient
                is TranscriptGpaAnalysis.Computed -> GpaAnalysisState.Ready(analysis)
            }
        }
    }
}
