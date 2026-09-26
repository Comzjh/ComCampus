package com.xmu.course.ui.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.domain.grades.GpaBaseline
import com.xmu.course.domain.grades.GpaCalculator
import com.xmu.course.domain.grades.GpaSandboxScales
import com.xmu.course.domain.grades.GradeInput
import com.xmu.course.domain.grades.GpaSimulationResult
import com.xmu.course.domain.grades.GradePointScale
import com.xmu.course.domain.grades.SimulatedCourse
import com.xmu.course.ui.academic.AcademicSimulationSeed
import com.xmu.course.ui.academic.AcademicSimulationSeedBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 学业模拟（GPA Sandbox）的会话状态控制器。
 *
 * 数据只来自本地已同步的教务缓存：培养方案快照（含本学期在修课）+ 成绩缓存 + 本地 GPA 策略。
 * 不读取 Excel、PDF、网络，也不写回任何来源；模拟结果永远只是本机推演。
 */
class GradesSandboxViewModel(
    private val scale: GradePointScale = GpaSandboxScales.commonLetter,
    academicStore: AcademicCompletionStore? = null,
    gradeStore: JwGradeStore? = null,
    policyStore: AcademicGpaPolicyStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradesSandboxState())
    val uiState: StateFlow<GradesSandboxState> = _uiState.asStateFlow()

    private var nextRowId = 1L
    private var currentSeed: AcademicSimulationSeed = AcademicSimulationSeed.EMPTY

    init {
        if (academicStore != null && gradeStore != null && policyStore != null) {
            viewModelScope.launch {
                combine(
                    academicStore.state,
                    gradeStore.state,
                    policyStore.policy,
                ) { planState, gradeState, policy ->
                    SeedUpdate(
                        seed = AcademicSimulationSeedBuilder.build(
                            completion = (planState as? AcademicCompletionStore.State.Loaded)?.snapshot,
                            grade = (gradeState as? JwGradeStore.State.Loaded)?.snapshot,
                            policy = policy,
                        ),
                        storageUnavailable = planState is AcademicCompletionStore.State.StorageFailure ||
                            gradeState is JwGradeStore.State.StorageFailure,
                    )
                }.collect { update -> applySeed(update) }
            }
        }
    }

    private data class SeedUpdate(
        val seed: AcademicSimulationSeed,
        val storageUnavailable: Boolean,
    )

    /**
     * 自动载入基线：按官方顺序注入本学期未结课课程，并保留用户对同号课程的既有编辑。
     * 课程从来源消失时对应自动行同步消失；自定义行永远保留。
     */
    private fun applySeed(update: SeedUpdate) {
        currentSeed = update.seed
        _uiState.update { state ->
            val existingBySeedKey = state.courses.filter { it.seeded }.associateBy { it.key }
            val seededRows = update.seed.simlatableCourses.map { course ->
                val key = SEEDED_COURSE_PREFIX + course.courseCode
                existingBySeedKey[key]?.copy(
                    name = course.courseName,
                    credits = course.creditsText.orEmpty(),
                    outsidePlan = course.outsidePlan,
                ) ?: SimulatedCourseInputState(
                    id = nextRowId++,
                    key = key,
                    name = course.courseName,
                    credits = course.creditsText.orEmpty(),
                    seeded = true,
                    outsidePlan = course.outsidePlan,
                )
            }
            state.copy(
                seed = update.seed,
                storageUnavailable = update.storageUnavailable,
                courses = seededRows + state.courses.filterNot { it.seeded },
                result = null,
                errorMessage = null,
            )
        }
    }

    // ---------------- 目标与手动基线 ----------------

    fun updateTargetGpa(value: String) {
        updateInput { it.copy(targetGpa = value) }
    }

    /** 手动调整模拟基线：默认关闭，且只影响本次模拟，不修改真实学业数据。 */
    fun setManualBaselineEnabled(enabled: Boolean) {
        updateInput { it.copy(manualBaselineEnabled = enabled) }
    }

    fun updateManualCurrentGpa(value: String) {
        updateInput { it.copy(manualCurrentGpa = value) }
    }

    fun updateManualCompletedCredits(value: String) {
        updateInput { it.copy(manualCompletedCredits = value) }
    }

    // ---------------- 模拟课程 ----------------

    /** 添加未来课程（What-if）：自定义行，与自动注入行互不影响。 */
    fun addCourse() {
        val id = nextRowId++
        updateInput {
            it.copy(
                courses = it.courses + SimulatedCourseInputState(
                    id = id,
                    key = CUSTOM_COURSE_PREFIX + id,
                ),
            )
        }
    }

    fun removeCourse(id: Long) {
        updateInput { state ->
            state.copy(courses = state.courses.filterNot { course -> course.id == id })
        }
    }

    fun updateCourseName(id: Long, value: String) = updateCourse(id) { it.copy(name = value) }

    fun updateCourseCredits(id: Long, value: String) = updateCourse(id) { it.copy(credits = value) }

    fun updateCourseGrade(id: Long, value: String) = updateCourse(id) { it.copy(grade = value) }

    /** 拖动滑块即视为「已设置模拟成绩」。 */
    fun updateCourseScore(id: Long, value: Int) =
        updateCourse(id) { it.copy(score = value.coerceIn(MIN_SCORE, MAX_SCORE), scoreSet = true) }

    /** 取消该课程的模拟成绩：回到未设置，不参与计算。 */
    fun clearCourseSimulation(id: Long) = updateCourse(id) {
        it.copy(grade = "", score = SimulatedCourseInputState.DEFAULT_SCORE, scoreSet = false)
    }

    /** 重置模拟：只恢复模拟会话状态，绝不触碰学业缓存。 */
    fun resetSimulation() {
        _uiState.update { state ->
            state.copy(
                manualBaselineEnabled = false,
                manualCurrentGpa = "",
                manualCompletedCredits = "",
                targetGpa = "",
                courses = currentSeed.simlatableCourses.map { course ->
                    SimulatedCourseInputState(
                        id = nextRowId++,
                        key = SEEDED_COURSE_PREFIX + course.courseCode,
                        name = course.courseName,
                        credits = course.creditsText.orEmpty(),
                        seeded = true,
                        outsidePlan = course.outsidePlan,
                    )
                },
                result = null,
                errorMessage = null,
            )
        }
    }

    // ---------------- 计算 ----------------

    fun simulate() {
        runCatching { resolveSimulation() }.onSuccess { result ->
            _uiState.update { it.copy(result = result, errorMessage = null) }
        }.onFailure { error ->
            _uiState.update {
                it.copy(result = null, errorMessage = error.message ?: "输入无效，请检查后重试")
            }
        }
    }

    private fun resolveSimulation(): GpaSimulationResult {
        val state = _uiState.value
        val baseline = resolveBaseline(state)
        val included = state.courses.filter { it.participates }
        require(included.isNotEmpty()) { "请先给至少一门课设置模拟成绩" }

        val courses = included.map { course ->
            SimulatedCourse(
                name = course.name.trim().ifEmpty {
                    throw IllegalArgumentException("模拟课程名称不能为空")
                },
                credits = parsePositive(course.credits, "课程学分"),
                grade = if (course.grade.trim().isNotEmpty()) {
                    GradeInput.Letter(course.grade.trim())
                } else {
                    GradeInput.Percentage(course.score.toDouble())
                },
            )
        }
        val targetGpa = state.targetGpa.trim().takeIf(String::isNotEmpty)?.let { parseGpa(it, "目标 GPA") }
        return GpaCalculator.simulate(
            baseline = baseline,
            courses = courses,
            scale = scale,
            targetGpa = targetGpa,
        )
    }

    /** 基线优先级：手动覆盖 > 教务缓存；两者都不可用时给出可恢复提示，绝不猜测。 */
    private fun resolveBaseline(state: GradesSandboxState): GpaBaseline {
        if (state.manualBaselineEnabled) {
            return GpaBaseline(
                gpa = parseGpa(state.manualCurrentGpa, "当前 GPA"),
                credits = parseNonNegative(state.manualCompletedCredits, "已修学分"),
            )
        }
        val baseline = state.seed.baseline
        val earnedCredits = baseline?.earnedCreditsText
        if (earnedCredits.isNullOrBlank()) {
            throw IllegalArgumentException(
                if (state.storageUnavailable) {
                    "本机学业缓存读取失败；可在「数据与来源」重新刷新，或在「数据管理」清除损坏缓存"
                } else {
                    // BUG-05：本机已有部分数据时不得再说"还没有数据"，只说清楚缺哪一半。
                    state.seed.availability.guidance
                        ?: "刷新学业数据后即可载入模拟基线。"
                },
            )
        }
        val gpaText = state.seed.usableGpaText
        if (gpaText == null) {
            throw IllegalArgumentException(
                if (state.seed.gpaNeedsConfirmation) {
                    "方案外课程尚未确认，无法确定 GPA 口径；请到「GPA 计算设置」选择是否计入"
                } else {
                    "现有成绩暂无可用绩点记录，不能确定起点；可手动调整模拟基线后继续"
                },
            )
        }
        return GpaBaseline(
            gpa = parseGpa(gpaText, "当前 GPA"),
            credits = parseNonNegative(earnedCredits, "已修学分"),
        )
    }

    // ---------------- 解析 ----------------

    private fun updateInput(transform: (GradesSandboxState) -> GradesSandboxState) {
        _uiState.update { transform(it) }
        refreshProjectedResult()
    }

    /**
     * 预计 GPA 实时跟随输入（UX-03）：不设目标 GPA 也能直接看到结果。
     *
     * 还没给任何一门课设分数时保持空白，不报"请先设置成绩"的噪音。
     */
    private fun refreshProjectedResult() {
        if (_uiState.value.courses.none { it.participates }) {
            _uiState.update { it.copy(result = null, errorMessage = null) }
            return
        }
        runCatching { resolveSimulation() }.onSuccess { result ->
            _uiState.update { it.copy(result = result, errorMessage = null) }
        }.onFailure {
            // 输入还没填完整时静默清空旧结果：打字过程中不报红，
            // 也绝不留着一个已过期的预计 GPA。原因由「计算模拟 GPA」明确给出。
            _uiState.update { it.copy(result = null, errorMessage = null) }
        }
    }

    private fun updateCourse(
        id: Long,
        transform: (SimulatedCourseInputState) -> SimulatedCourseInputState,
    ) {
        updateInput { state ->
            state.copy(
                courses = state.courses.map { course ->
                    if (course.id == id) transform(course) else course
                },
            )
        }
    }

    private fun parseGpa(value: String, label: String): Double {
        val parsed = parseNonNegative(value, label)
        require(parsed <= MAX_GPA) { label + " 必须在 0 到 " + MAX_GPA + " 之间" }
        return parsed
    }

    private fun parseNonNegative(value: String, label: String): Double {
        val parsed = value.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException(label + " 必须是数字")
        require(parsed.isFinite() && parsed >= 0.0) { label + " 必须是非负数" }
        return parsed
    }

    private fun parsePositive(value: String, label: String): Double {
        val parsed = value.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException(label + " 必须是数字")
        require(parsed.isFinite() && parsed > 0.0) { label + " 必须大于 0" }
        return parsed
    }

    private companion object {
        const val MIN_SCORE = 60
        const val MAX_SCORE = 100

        /**
         * 厦大绩点为 0~5 口径，故 GPA 上限按 5.0 校验；
         * 若沿用 4.0 上限，本地计算 GPA 高于 4.0 的真实基线会被误判为非法输入。
         * 模拟课程侧仍使用通用 4.0 制换算表，这是既有产品口径，本里程碑不改计算引擎。
         */
        const val MAX_GPA = 5.0
    }
}
