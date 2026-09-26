package com.xmu.course.ui.academic

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xmu.course.XmuCourseApplication
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.di.AppContainer
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 学业首页状态：概览 / GPA / 历史学期摘要。
 * 「学业」模块只读消费官方快照，任何写操作都只发生在本地 store。
 */
data class AcademicHomeUiState(
    val snapshot: AcademicCompletionSnapshot? = null,
    val planStorageFailed: Boolean = false,
    val gradeStorageFailed: Boolean = false,
    val overview: AcademicOverviewRow? = null,
    val gpa: AcademicGpaSummary = AcademicGpaSummary(tone = AcademicGpaTone.NO_DATA),
    val historySemesters: List<AcademicSemesterRow> = emptyList(),
    val gradeCourseCount: Int = 0,
    val planFetchedAtText: String? = null,
    /** 本机存在有效成绩快照，包括成功查询后 0 行的情况。 */
    val gradeSnapshotLoaded: Boolean = false,
    val gradeRefreshedAtText: String? = null,
    val policy: OutsidePlanGpaPolicy = OutsidePlanGpaPolicy.UNCONFIRMED,
    /** 本学期可参与模拟的在修课程数（学分可得；仅展示，不参与计算）。 */
    val simlatableCourseCount: Int = 0,
) {
    /** 两张官方缓存都没有已加载快照且无故障 = 首次使用空态。 */
    val isFreshEmpty: Boolean
        get() = snapshot == null && !gradeSnapshotLoaded && gradeCourseCount == 0 &&
            !planStorageFailed && !gradeStorageFailed
}

/** 学业首页聚合：培养方案快照 + 官方成绩缓存 + 本地 GPA 策略，全部离线可读。 */
class AcademicHomeViewModel(
    academicStore: AcademicCompletionStore,
    gradeStore: JwGradeStore,
    policyStore: AcademicGpaPolicyStore,
) : ViewModel() {

    val uiState: StateFlow<AcademicHomeUiState> = combine(
        academicStore.state,
        gradeStore.state,
        policyStore.policy,
    ) { planState, gradeState, policy ->
        val snapshot = (planState as? AcademicCompletionStore.State.Loaded)?.snapshot
        val grade = (gradeState as? JwGradeStore.State.Loaded)?.snapshot
        val outsideCodes = AcademicModuleAggregator.outsidePlanCourseCodes(snapshot)
        AcademicHomeUiState(
            snapshot = snapshot,
            planStorageFailed = planState is AcademicCompletionStore.State.StorageFailure,
            gradeStorageFailed = gradeState is JwGradeStore.State.StorageFailure,
            overview = AcademicModuleAggregator.overview(snapshot),
            gpa = AcademicModuleAggregator.gpaSummary(
                grade = grade,
                outsideCodes = outsideCodes,
                policy = policy,
                planSynced = snapshot != null,
            ),
            historySemesters = AcademicModuleAggregator.historySemesters(grade, outsideCodes).map { it.row },
            gradeCourseCount = grade?.entries?.size ?: 0,
            gradeSnapshotLoaded = grade != null,
            planFetchedAtText = snapshot?.fetchedAtEpochMillis?.let(::formatAcademicRefreshTime),
            gradeRefreshedAtText = grade?.let { formatAcademicRefreshTime(it.refreshedAtEpochMillis) },
            policy = policy,
            simlatableCourseCount = snapshot?.enrolledCourses?.count { snapshot.effectiveCreditsText(it) != null } ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicHomeUiState())
}

/** 数据与来源页状态：本机三仓库存量 + 最近刷新时间，只读展示，不触发任何网络访问。 */
data class AcademicDataSourcesUiState(
    val planSynced: Boolean = false,
    val planStorageFailed: Boolean = false,
    val planSnapshotAt: String? = null,
    val planFetchedAtText: String? = null,
    val planName: String? = null,
    val gradeSynced: Boolean = false,
    val gradeStorageFailed: Boolean = false,
    val gradeRefreshedAtText: String? = null,
    val gradeCourseCount: Int = 0,
    val enrolledCount: Int = 0,
    val pendingManualCount: Int = 0,
) {
    val hasAnyData: Boolean
        get() = planSynced || gradeSynced

    val hasStorageFailure: Boolean
        get() = planStorageFailed || gradeStorageFailed
}

class AcademicDataSourcesViewModel(
    academicStore: AcademicCompletionStore,
    gradeStore: JwGradeStore,
) : ViewModel() {

    val uiState: StateFlow<AcademicDataSourcesUiState> = combine(
        academicStore.state,
        gradeStore.state,
    ) { planState, gradeState ->
        val snapshot = (planState as? AcademicCompletionStore.State.Loaded)?.snapshot
        val grade = (gradeState as? JwGradeStore.State.Loaded)?.snapshot
        AcademicDataSourcesUiState(
            planSynced = snapshot != null,
            planStorageFailed = planState is AcademicCompletionStore.State.StorageFailure,
            planSnapshotAt = snapshot?.plan?.sourceSnapshotAt,
            planFetchedAtText = snapshot?.fetchedAtEpochMillis?.let(::formatAcademicRefreshTime),
            planName = snapshot?.plan?.planName,
            gradeSynced = grade != null,
            gradeStorageFailed = gradeState is JwGradeStore.State.StorageFailure,
            gradeRefreshedAtText = grade?.let { formatAcademicRefreshTime(it.refreshedAtEpochMillis) },
            gradeCourseCount = grade?.entries?.size ?: 0,
            enrolledCount = snapshot?.enrolledCourses?.size ?: 0,
            pendingManualCount = snapshot?.pendingManualCourses?.size ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicDataSourcesUiState())
}

/** 历史成绩页状态：学期分组 + 用户选择的学期过滤（默认全部）。 */
data class AcademicHistoryUiState(
    val semesters: List<AcademicHistorySemester> = emptyList(),
    val selectedSemesterCode: String? = null,
    val storageFailed: Boolean = false,
) {
    val visibleSemesters: List<AcademicHistorySemester>
        get() = selectedSemesterCode?.let { code -> semesters.filter { it.row.semesterCode == code } } ?: semesters

    companion object {
        val EMPTY = AcademicHistoryUiState()
    }
}

class AcademicHistoryViewModel(
    academicStore: AcademicCompletionStore,
    gradeStore: JwGradeStore,
) : ViewModel() {

    private val selected = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AcademicHistoryUiState> = combine(
        academicStore.state,
        gradeStore.state,
        selected,
    ) { planState, gradeState, selection ->
        val snapshot = (planState as? AcademicCompletionStore.State.Loaded)?.snapshot
        val grade = (gradeState as? JwGradeStore.State.Loaded)?.snapshot
        AcademicHistoryUiState(
            semesters = AcademicModuleAggregator.historySemesters(
                grade = grade,
                outsideCodes = AcademicModuleAggregator.outsidePlanCourseCodes(snapshot),
            ),
            selectedSemesterCode = selection?.takeIf { wanted ->
                grade?.let { g -> g.groupedBySemester().any { it.first == wanted } } == true
            },
            storageFailed = gradeState is JwGradeStore.State.StorageFailure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicHistoryUiState.EMPTY)

    fun selectSemester(semesterCode: String?) {
        selected.update { semesterCode }
    }
}

/** GPA 页状态：派生 GPA + 方案外候选清单 + 策略裁决入口。 */
data class AcademicGpaUiState(
    val summary: AcademicGpaSummary = AcademicGpaSummary(tone = AcademicGpaTone.NO_DATA),
    val candidates: List<Pair<String, String>> = emptyList(),
    val policy: OutsidePlanGpaPolicy = OutsidePlanGpaPolicy.UNCONFIRMED,
)

class AcademicGpaViewModel(
    academicStore: AcademicCompletionStore,
    gradeStore: JwGradeStore,
    private val policyStore: AcademicGpaPolicyStore,
) : ViewModel() {

    val uiState: StateFlow<AcademicGpaUiState> = combine(
        academicStore.state,
        gradeStore.state,
        policyStore.policy,
    ) { planState, gradeState, policy ->
        val snapshot = (planState as? AcademicCompletionStore.State.Loaded)?.snapshot
        val grade = (gradeState as? JwGradeStore.State.Loaded)?.snapshot
        val outsideCodes = AcademicModuleAggregator.outsidePlanCourseCodes(snapshot)
        AcademicGpaUiState(
            summary = AcademicModuleAggregator.gpaSummary(
                grade = grade,
                outsideCodes = outsideCodes,
                policy = policy,
                planSynced = snapshot != null,
            ),
            candidates = grade?.entries
                ?.filter { it.courseCode in outsideCodes }
                ?.map { it.courseName to it.semesterDisplay.ifBlank { it.semesterCode } }
                .orEmpty(),
            policy = policy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicGpaUiState())

    /** 用户裁决方案外课程是否计入本地 GPA；只写本地偏好，官方数据与学校状态不受影响。 */
    fun setPolicy(policy: OutsidePlanGpaPolicy) {
        policyStore.setPolicy(policy)
    }
}

/** 组合根工厂：UI 不自行构造存储；学业模块所有 ViewModel 从 AppContainer 一处装配。 */
object AcademicViewModels {
    fun homeFactory(application: Application): ViewModelProvider.Factory =
        containerFactory(application) { container ->
            AcademicHomeViewModel(
                academicStore = container.academicCompletionStore,
                gradeStore = container.jwGradeStore,
                policyStore = container.academicGpaPolicyStore,
            )
        }

    fun dataSourcesFactory(application: Application): ViewModelProvider.Factory =
        containerFactory(application) { container ->
            AcademicDataSourcesViewModel(
                academicStore = container.academicCompletionStore,
                gradeStore = container.jwGradeStore,
            )
        }

    fun historyFactory(application: Application): ViewModelProvider.Factory =
        containerFactory(application) { container ->
            AcademicHistoryViewModel(
                academicStore = container.academicCompletionStore,
                gradeStore = container.jwGradeStore,
            )
        }

    fun gpaFactory(application: Application): ViewModelProvider.Factory =
        containerFactory(application) { container ->
            AcademicGpaViewModel(
                academicStore = container.academicCompletionStore,
                gradeStore = container.jwGradeStore,
                policyStore = container.academicGpaPolicyStore,
            )
        }

    private fun containerFactory(
        application: Application,
        create: (AppContainer) -> ViewModel,
    ): ViewModelProvider.Factory = AppContainerViewModelFactory(application, create)
}

/** 通用工厂：从组合根取依赖；与既有 *ViewModelFactory 同一装配纪律。 */
private class AppContainerViewModelFactory(
    private val application: Application,
    private val create: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = application as? XmuCourseApplication
            ?: error("Academic ViewModels require XmuCourseApplication")
        return create(app.appContainer) as T
    }
}
