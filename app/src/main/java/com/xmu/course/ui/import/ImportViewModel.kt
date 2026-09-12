package com.xmu.course.ui.import

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.ImportPreparation
import com.xmu.course.data.ImportResult
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "XmuImport"

/** 导入页 UI 状态。[conflict] 非 null 时 UI 弹出“覆盖/取消”对话框。 */
data class ImportUiState(
    val message: String? = null,
    val conflict: ImportPreparation.Conflict? = null,
)

/**
 * 导入 ViewModel：HTML（WebView 抓取或本地文件）→ Parser → Repository → Room。
 */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(AppDatabase.getInstance(application))

    // 导入成功后为学期绑定课表（细节隐藏在 TimetableRepository 内）。
    private val timetableRepository = TimetableRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** 保存 WebView 抓取的 HTML 并导入数据库。 */
    fun saveCourseHtml(html: String) {
        import(html, source = "保存课表")
    }

    /** 保存用户选择的本地 HTML 并导入数据库。 */
    fun saveImportedHtml(html: String) {
        if (html.isBlank()) {
            _uiState.update { it.copy(message = "读取文件失败") }
            return
        }
        import(html, source = "本地导入")
    }

    private fun import(html: String, source: String) {
        // 调试日志：确认 Parser 输入包含课表内容（Bug 1 排查用）。
        Log.d(
            TAG,
            "import($source): html.length=${html.length}, hasGrid=${html.contains("jsTbl_01")}, " +
                "hasArrage=${html.contains("arrage")}, head=${html.take(500).replace('\n', ' ')}",
        )
        viewModelScope.launch {
            try {
                when (val prep = repository.prepareImport(html)) {
                    is ImportPreparation.Ready -> {
                        Log.d(TAG, "parser 输出：${prep.courses.size} 门课程")
                        when (val r = repository.commitImport(prep.semester, prep.courses, overwrite = false)) {
                            is ImportResult.Success -> {
                                timetableRepository.ensureForSemester(r.semesterId, prep.semester.name, prep.semester.startDate)
                                _uiState.update {
                                    it.copy(message = importSuccessMessage(prep.semester.name, r.count, prep.courses))
                                }
                            }
                            ImportResult.Cancelled ->
                                _uiState.update { it.copy(message = "导入已取消") }
                        }
                    }
                    is ImportPreparation.Conflict -> {
                        Log.d(TAG, "parser 输出：冲突（新 ${prep.pendingCourses.size} 门）")
                        _uiState.update { it.copy(conflict = prep) }
                    }
                }
            } catch (e: com.xmu.course.data.ImportEmptyException) {
                Log.w(TAG, "解析结果为空：${e.warnings}")
                _uiState.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                Log.e(TAG, "导入失败", e)
                _uiState.update { it.copy(message = "$source 失败：${e.message}") }
            }
        }
    }

    /** 用户确认覆盖导入。 */
    fun confirmOverwrite() {
        val conflict = _uiState.value.conflict ?: return
        _uiState.update { it.copy(conflict = null) }
        viewModelScope.launch {
            when (val r = repository.commitImport(conflict.semester, conflict.pendingCourses, overwrite = true)) {
                is ImportResult.Success -> {
                    timetableRepository.ensureForSemester(r.semesterId, conflict.semester.name, conflict.semester.startDate)
                    _uiState.update {
                        it.copy(message = importSuccessMessage(conflict.semester.name, r.count, conflict.pendingCourses))
                    }
                }
                ImportResult.Cancelled ->
                    _uiState.update { it.copy(message = "导入已取消") }
            }
        }
    }

    /** 用户取消覆盖。 */
    fun cancelOverwrite() {
        _uiState.update { it.copy(conflict = null, message = "导入已取消") }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** 成功提示：学期 + 课程数 + 周数范围。 */
    private fun importSuccessMessage(semesterName: String, count: Int, courses: List<Course>): String {
        val weeks = courses.flatMap { it.weeks }
        val range = if (weeks.isEmpty()) "" else "，第 ${weeks.min()}-${weeks.max()} 周"
        return "导入成功：$semesterName，$count 门课程$range"
    }

    companion object {
        const val IMPORTED_TEMP_FILE = "imported_course.html"
    }
}


