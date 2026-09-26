package com.xmu.course.ui.academic

import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.CompletedCourseOutsidePlan
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 学业 ViewModel 聚合测试（合成数据）。
 *
 * 锁定：官方两源快照 + 本地策略的只读聚合、离线可读、
 * 用户策略切换只改变派生 GPA 且不触碰任何官方缓存文件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicViewModelsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var policyStore: AcademicGpaPolicyStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        policyStore = AcademicGpaPolicyStore(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun file(name: String) = File(tempFolder.root, name)

    private fun planStore(snapshot: AcademicCompletionSnapshot? = null): AcademicCompletionStore {
        val store = AcademicCompletionStore(file("plan.json"))
        if (snapshot != null) assertTrue(store.import(snapshot))
        return store
    }

    private fun gradeStore(vararg entries: JwGradeEntry): JwGradeStore {
        val store = JwGradeStore(file("grades.json"))
        if (entries.isNotEmpty()) {
            assertTrue(
                store.replace(
                    JwGradeSnapshot(
                        refreshedAtEpochMillis = 1_770_000_000_000L,
                        providerId = "xmu.jw",
                        sourceCapability = "cjcx.xscjcx",
                        totalCreditsText = "6",
                        entries = entries.toList(),
                    ),
                ),
            )
        }
        return store
    }

    private fun loadedEmptyGradeStore(): JwGradeStore {
        val store = JwGradeStore(file("empty-grades.json"))
        assertTrue(
            store.replace(
                JwGradeSnapshot(
                    refreshedAtEpochMillis = 1_770_000_000_000L,
                    providerId = "xmu.jw",
                    sourceCapability = "cjcx.xscjcx",
                    totalCreditsText = "0",
                    entries = emptyList(),
                ),
            ),
        )
        return store
    }

    private fun sourceCourse(code: String, inPlan: Boolean = true) = SourceCourse(
        courseCode = code,
        courseName = "合成课程$code",
        creditsText = if (inPlan) "3" else null,
        status = if (inPlan) SourceXfStatus.CONFIRMED else SourceXfStatus.NEEDS_MANUAL,
        inPlan = inPlan,
        teacherNames = "合成教师",
        classCode = "CL01",
        confirmationHint = null,
    )

    private fun snapshot() = AcademicCompletionSnapshot(
        schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
        semesterLabel = "2026-2027-1",
        plan = PlanSummary("合成培养方案", "160", "40", "2026-09-16 22:09", "3"),
        enrolledCourses = listOf(sourceCourse("C001"), sourceCourse("X001", inPlan = false)),
        completedCoursesOutsidePlan = listOf(
            CompletedCourseOutsidePlan("Y001", "方案外结课", "2", "2024-2025-2", "80"),
        ),
        localOverrides = emptyMap(),
        fetchedAtEpochMillis = 1_780_000_000_000L,
    )

    private fun gradeEntry(code: String, semester: String = "2025-2026-1") = JwGradeEntry(
        rowId = "R$code",
        semesterCode = semester,
        semesterDisplay = "$semester 学期",
        courseCode = code,
        courseName = "历史课程$code",
        creditsText = "3",
        gradeText = "87",
        pointGradeText = "3.7",
        courseNatureDisplay = "必修",
        courseCategoryDisplay = null,
        offeringUnitDisplay = null,
        retakeCode = null,
    )

    private fun homeViewModel(
        plan: AcademicCompletionStore = planStore(),
        grades: JwGradeStore = gradeStore(),
    ) = AcademicHomeViewModel(plan, grades, policyStore)

    // ---------- 首页 ----------

    @Test
    fun freshEmptyWhenNoOfficialCache() = runTest {
        val state = homeViewModel().uiState.first()
        assertTrue(state.isFreshEmpty)
        assertNull(state.overview)
        assertEquals(AcademicGpaTone.NO_DATA, state.gpa.tone)
        assertTrue(state.historySemesters.isEmpty())
    }

    @Test
    fun successfulEmptyGradeSnapshotIsNotFirstRunEmpty() = runTest {
        val state = homeViewModel(
            plan = planStore(),
            grades = loadedEmptyGradeStore(),
        ).uiState.first()

        assertTrue(state.gradeSnapshotLoaded)
        assertEquals(0, state.gradeCourseCount)
        assertTrue(!state.isFreshEmpty)
        assertTrue(state.gradeRefreshedAtText != null)
    }

    @Test
    fun offlineAggregatesBothOfficialCaches() = runTest {
        val state = homeViewModel(
            plan = planStore(snapshot()),
            grades = gradeStore(gradeEntry("C001"), gradeEntry("X001")),
        ).uiState.first()
        assertTrue(!state.isFreshEmpty)
        assertEquals("合成培养方案", state.overview?.planName)
        assertEquals(2, state.gradeCourseCount)
        assertEquals(listOf("2025-2026-1"), state.historySemesters.map { it.semesterCode })
        assertEquals(2, state.historySemesters.single().courseCount)
        assertTrue(state.gradeRefreshedAtText!!.contains("20"))
        assertEquals(formatAcademicRefreshTime(1_780_000_000_000L), state.planFetchedAtText)
    }

    @Test
    fun dataSourcesKeepSchoolTimeSeparateFromLocalFetchTime() = runTest {
        val state = AcademicDataSourcesViewModel(
            academicStore = planStore(snapshot()),
            gradeStore = gradeStore(),
        ).uiState.first()

        assertEquals("2026-09-16 22:09", state.planSnapshotAt)
        assertEquals(formatAcademicRefreshTime(1_780_000_000_000L), state.planFetchedAtText)
    }

    @Test
    fun outsidePlanCandidateForcesConfirmationInsteadOfNumber() = runTest {
        val state = homeViewModel(
            plan = planStore(snapshot()),
            grades = gradeStore(gradeEntry("C001"), gradeEntry("X001")),
        ).uiState.first()
        assertEquals(AcademicGpaTone.NEEDS_CONFIRMATION, state.gpa.tone)
        assertNull(state.gpa.valueText)
        assertEquals(OutsidePlanGpaPolicy.UNCONFIRMED, state.policy)
    }

    @Test
    fun userPolicyDecisionOnlyChangesDerivedGpa() = runTest {
        val plan = planStore(snapshot())
        val grades = gradeStore(gradeEntry("C001"), gradeEntry("X001"))
        val before = plan.current().toString() + grades.current().toString()
        policyStore.setPolicy(OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN)
        val state = homeViewModel(plan, grades).uiState.first()
        assertEquals(AcademicGpaTone.COMPUTED, state.gpa.tone)
        assertEquals("3.70", state.gpa.valueText)
        assertEquals(1, state.gpa.excludedByPolicy)
        // 官方缓存文本与来源事实完全未被派生计算改写
        assertEquals(before, plan.current().toString() + grades.current().toString())
    }

    @Test
    fun storageFailuresAreSurfacedSeparately() = runTest {
        val brokenPlanFile = file("broken-plan.json").also {
            it.parentFile?.mkdirs()
            it.writeText("{not-json")
        }
        val brokenGradeFile = file("broken-grades.json").also {
            it.parentFile?.mkdirs()
            it.writeText("{not-json")
        }
        val state = homeViewModel(
            plan = AcademicCompletionStore(brokenPlanFile),
            grades = JwGradeStore(brokenGradeFile),
        ).uiState.first()
        assertTrue(state.planStorageFailed)
        assertTrue(state.gradeStorageFailed)
        assertTrue(!state.isFreshEmpty)
    }

    // ---------- 历史成绩 ----------

    @Test
    fun historyGroupsAllSemestersAndFiltersOnSelection() = runTest {
        val viewModel = AcademicHistoryViewModel(
            planStore(snapshot()),
            gradeStore(gradeEntry("C001"), gradeEntry("C002", semester = "2025-2026-2")),
        )
        val all = viewModel.uiState.first()
        assertEquals(2, all.semesters.size)
        assertEquals(2, all.visibleSemesters.size)
        viewModel.selectSemester("2025-2026-2")
        val filtered = viewModel.uiState.first()
        assertEquals(listOf("2025-2026-2"), filtered.visibleSemesters.map { it.row.semesterCode })
    }

    @Test
    fun historyIgnoresUnknownSemesterSelection() = runTest {
        val viewModel = AcademicHistoryViewModel(planStore(), gradeStore(gradeEntry("C001")))
        viewModel.selectSemester("1999-2000-1")
        val state = viewModel.uiState.first()
        assertNull(state.selectedSemesterCode)
        assertEquals(1, state.visibleSemesters.size)
    }

    @Test
    fun historyEmptyWithoutGradeCache() = runTest {
        val state = AcademicHistoryViewModel(planStore(), gradeStore()).uiState.first()
        assertTrue(state.semesters.isEmpty())
        assertTrue(!state.storageFailed)
    }

    // ---------- GPA 页 ----------

    @Test
    fun gpaListsOutsidePlanCandidatesWithSemesterContext() = runTest {
        val state = AcademicGpaViewModel(
            academicStore = planStore(snapshot()),
            gradeStore = gradeStore(gradeEntry("C001"), gradeEntry("X001")),
            policyStore = policyStore,
        ).uiState.first()
        assertEquals(AcademicGpaTone.NEEDS_CONFIRMATION, state.summary.tone)
        assertEquals(listOf("历史课程X001" to "2025-2026-1 学期"), state.candidates)
    }

    @Test
    fun gpaPolicyChangeTakesEffectImmediately() = runTest {
        val viewModel = AcademicGpaViewModel(
            academicStore = planStore(snapshot()),
            gradeStore = gradeStore(gradeEntry("C001"), gradeEntry("X001")),
            policyStore = policyStore,
        )
        assertTrue(viewModel.uiState.first().summary.valueText == null)
        viewModel.setPolicy(OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN)
        val after = viewModel.uiState.first()
        assertEquals(OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN, after.policy)
        assertEquals(AcademicGpaTone.COMPUTED, after.summary.tone)
        assertEquals(2, after.summary.countedCourses)
    }

    @Test
    fun gpaFlagsUnverifiedAttributionWhenPlanMissing() = runTest {
        val state = AcademicGpaViewModel(
            academicStore = planStore(),
            gradeStore = gradeStore(gradeEntry("C001")),
            policyStore = policyStore,
        ).uiState.first()
        assertTrue(!state.summary.planSynced)
        assertTrue(state.candidates.isEmpty())
        assertEquals(AcademicGpaTone.COMPUTED, state.summary.tone)
    }
}
