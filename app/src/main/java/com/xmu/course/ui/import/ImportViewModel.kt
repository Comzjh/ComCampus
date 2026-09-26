package com.xmu.course.ui.import

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult
import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester
import com.xmu.course.domain.isValidFirstWeekStartDate
import com.xmu.course.ui.OneShotToken
import com.xmu.course.ui.import.model.ImportConflictModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/** 导入页 UI 状态。[conflict] 非 null 时 UI 弹出“覆盖/取消”对话框。 */
data class ImportUiState(
    val message: String? = null,
    val importCompleted: Boolean = false,
    /** 一次性完成令牌：每次成功导入 +1，导航层据此去重回跳（BUG-01）。 */
    val completionToken: Long = 0L,
    val conflict: ImportConflictModel? = null,
    val shouldShowStartDatePicker: Boolean = false,
    val startDateError: String? = null,
)

private data class PendingStartDate(
    val semester: Semester,
    val courses: List<Course>,
    val overwrite: Boolean,
    val source: String,
)

/**
 * 导入 ViewModel：HTML（WebView 抓取或本地文件）→ Parser → Repository → Room。
 */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val importCoordinator = (application as? XmuCourseApplication)
        ?.appContainer
        ?.importCoordinator
        ?: error("ImportViewModel requires XmuCourseApplication")
    private var pendingConflict: ImportPreparation.Conflict? = null
    private var pendingStartDate: PendingStartDate? = null

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    private val completionClaim = OneShotToken()

    /**
     * 消费"导入成功"事件：同一令牌只有第一次调用返回 true。
     *
     * 导航回跳归属集中在这里，导入页与 WebView 页不再各自竞速 popBackStack。
     */
    fun claimImportCompletion(): Boolean = completionClaim.claim(_uiState.value.completionToken)

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
        viewModelScope.launch {
            try {
                when (val prep = importCoordinator.prepareImport(html)) {
                    is ImportPreparation.Ready -> {
                        continueImport(prep, overwrite = false, source)
                    }
                    is ImportPreparation.Conflict -> {
                        pendingConflict = prep
                        _uiState.update {
                            it.copy(
                                conflict = ImportConflictModel(
                                    semester = prep.semester,
                                    existingCount = prep.existingCount,
                                    newCount = prep.newCount,
                                ),
                            )
                        }
                    }
                }
            } catch (e: com.xmu.course.data.ImportEmptyException) {
                _uiState.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "$source 失败：${e.message}") }
            }
        }
    }

    /** 用户确认覆盖导入。 */
    fun confirmOverwrite() {
        val conflict = pendingConflict ?: return
        pendingConflict = null
        _uiState.update { it.copy(conflict = null) }
        continueImport(conflict, overwrite = true, source = "覆盖导入")
    }

    /** 用户取消覆盖。 */
    fun cancelOverwrite() {
        pendingConflict = null
        _uiState.update { it.copy(conflict = null, message = "导入已取消") }
    }

    fun cancelStartDate() {
        pendingStartDate = null
        _uiState.update { it.copy(shouldShowStartDatePicker = false, startDateError = null, message = "导入已取消") }
    }

    fun confirmStartDate(selectedDateMillis: Long?) {
        val pending = pendingStartDate ?: return
        val date = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        if (date == null) {
            _uiState.update { it.copy(startDateError = "请选择第一周星期一作为开学日期") }
            return
        }
        if (!isValidFirstWeekStartDate(date)) {
            _uiState.update { it.copy(startDateError = "开学日期必须是星期一，请选择第一周的星期一") }
            return
        }
        pendingStartDate = null
        _uiState.update { it.copy(shouldShowStartDatePicker = false, startDateError = null) }
        commitPreparation(pending.semester.copy(startDate = date.toString()), pending.courses, pending.overwrite)
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null, importCompleted = false) }
    }

    private fun continueImport(preparation: ImportPreparation, overwrite: Boolean, source: String) {
        val semester = when (preparation) {
            is ImportPreparation.Ready -> preparation.semester
            is ImportPreparation.Conflict -> preparation.semester
        }
        val courses = when (preparation) {
            is ImportPreparation.Ready -> preparation.courses
            is ImportPreparation.Conflict -> preparation.pendingCourses
        }
        if (semester.startDate == null) {
            pendingStartDate = PendingStartDate(semester, courses, overwrite, source)
            _uiState.update {
                it.copy(
                    shouldShowStartDatePicker = true,
                    startDateError = null,
                )
            }
        } else {
            commitPreparation(semester, courses, overwrite)
        }
    }

    private fun commitPreparation(semester: Semester, courses: List<Course>, overwrite: Boolean) {
        viewModelScope.launch {
            try {
                when (val r = importCoordinator.commitImport(semester, courses, overwrite)) {
                    is ImportResult.Success -> {
                        _uiState.update {
                            it.copy(
                                message = importSuccessMessage(semester.name, r.count, courses),
                                importCompleted = true,
                                completionToken = it.completionToken + 1,
                            )
                        }
                    }
                    ImportResult.Cancelled -> _uiState.update { it.copy(message = "导入已取消") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "导入失败，请稍后重试") }
            }
        }
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


