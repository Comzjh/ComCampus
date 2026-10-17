package com.xmu.course.ui.academic

import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.academic.AcademicGpaPolicyStore
import com.xmu.course.data.academiccompletion.ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION
import com.xmu.course.data.academiccompletion.AcademicCompletionSnapshot
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.academiccompletion.PlanSummary
import com.xmu.course.data.academiccompletion.SourceCourse
import com.xmu.course.data.academiccompletion.SourceXfStatus
import com.xmu.course.data.jwgrades.JwGradeEntry
import com.xmu.course.data.jwgrades.JwGradeSnapshot
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import com.xmu.course.ui.grades.GradesSandboxViewModel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bounded academic audit: synthetic fixtures only, no school requests or real data.
 * These assertions describe the expected behavior and expose the audited defects;
 * they must not be weakened to match the current incorrect outputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicCalculationAuditRegressionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var policy: AcademicGpaPolicyStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        policy = AcademicGpaPolicyStore(ApplicationProvider.getApplicationContext())
        policy.setPolicy(OutsidePlanGpaPolicy.INCLUDE_OUTSIDE_PLAN)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun simulationMustNotReweightTheRoundedDisplayGpa() = runTest {
        val planStore = AcademicCompletionStore(File(temporaryFolder.root, "plan.json"))
        val gradeStore = gradeStore(
            grade("SYN-A", credits = "1", points = "2.0"),
            grade("SYN-B", credits = "3", points = "2.3"),
        )
        val viewModel = GradesSandboxViewModel(
            academicStore = planStore,
            gradeStore = gradeStore,
            policyStore = policy,
        )
        // Historical weighted points = 8.9, credits = 4, unrounded GPA = 2.225.
        assertEquals("2.23", viewModel.uiState.value.seed.usableGpaText)
        viewModel.addCourse()
        val courseId = viewModel.uiState.value.courses.single().id
        viewModel.updateCourseName(courseId, "Synthetic future course")
        viewModel.updateCourseCredits(courseId, "2")
        viewModel.updateCourseGrade(courseId, "B-") // Existing explicit scale: 2.7.
        viewModel.updateTargetGpa("2.385")
        viewModel.simulate()

        val result = viewModel.uiState.value.result
        assertNotNull(result)
        requireNotNull(result)
        // (8.9 + 2 * 2.7) / 6 = 2.383333..., displayed as 2.38.
        assertEquals("2.38", String.format(Locale.US, "%.2f", result.gpa))
        assertEquals(14.3 / 6.0, result.gpa, 1e-10)
        assertTrue("The actual weighted GPA is still below the chosen target", result.targetGap!! > 0)
    }

    @Test
    fun homeSimulationCountMustMatchTheCoursesThatTheSandboxCanEdit() = runTest {
        val plan = AcademicCompletionSnapshot(
            schemaVersion = ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION,
            semesterLabel = "2026-2027-1",
            plan = PlanSummary("Synthetic plan", "100", "3", "synthetic", "5"),
            enrolledCourses = listOf(sourceCourse("SYN-GRADED", "3"), sourceCourse("SYN-OPEN", "2")),
            completedCoursesOutsidePlan = emptyList(),
            localOverrides = emptyMap(),
        )
        val planStore = AcademicCompletionStore(File(temporaryFolder.root, "plan.json"))
        assertTrue(planStore.import(plan))
        val grades = snapshot(grade("SYN-GRADED", credits = "3", points = "3.0"))
        val gradeStore = JwGradeStore(File(temporaryFolder.root, "grades.json"))
        assertTrue(gradeStore.replace(grades))

        val home = AcademicHomeViewModel(planStore, gradeStore, policy).uiState.first { it.gradeSnapshotLoaded }
        val seed = AcademicSimulationSeedBuilder.build(plan, grades, policy.policy.value)
        assertEquals(listOf("SYN-OPEN"), seed.simlatableCourses.map { it.courseCode })
        assertEquals("Homepage promises the number of editable simulation courses", 1, home.simlatableCourseCount)
        assertEquals(seed.simlatableCourses.size, home.simlatableCourseCount)
    }

    private fun gradeStore(vararg entries: JwGradeEntry): JwGradeStore =
        JwGradeStore(File(temporaryFolder.root, "grades.json")).also {
            assertTrue(it.replace(snapshot(*entries)))
        }

    private fun snapshot(vararg entries: JwGradeEntry) = JwGradeSnapshot(
        refreshedAtEpochMillis = 1_800_000_000_000L,
        providerId = "synthetic",
        sourceCapability = "synthetic",
        totalCreditsText = entries.sumOf { it.creditsText.toDouble() }.toString(),
        entries = entries.toList(),
    )

    private fun grade(code: String, credits: String, points: String) = JwGradeEntry(
        rowId = "ROW-$code",
        semesterCode = "2026-2027-1",
        semesterDisplay = "Synthetic semester",
        courseCode = code,
        courseName = "Synthetic $code",
        creditsText = credits,
        gradeText = "80",
        pointGradeText = points,
        courseNatureDisplay = null,
        courseCategoryDisplay = null,
        offeringUnitDisplay = null,
        retakeCode = null,
    )

    private fun sourceCourse(code: String, credits: String) = SourceCourse(
        courseCode = code,
        courseName = "Synthetic $code",
        creditsText = credits,
        status = SourceXfStatus.CONFIRMED,
        inPlan = true,
        teacherNames = "Synthetic teacher",
        classCode = "CLASS-$code",
        confirmationHint = null,
    )
}
