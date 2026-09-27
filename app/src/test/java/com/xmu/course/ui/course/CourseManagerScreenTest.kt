package com.xmu.course.ui.course

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.CourseManagementContract
import com.xmu.course.contracts.TimetableObservationContract
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 新手引导第 7 页承诺「长按卡片可删除课程」，这里把它锁成回归：
 * 长按必须弹出按课程定位的二次确认，取消零删除，确认只删被按下的那一门。
 * 清空同样是危险操作，必须经过确认，且取消不得触碰任何数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class CourseManagerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repo: FakeCourseRepo

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimetablePrefs.setCurrent(ApplicationProvider.getApplicationContext(), TIMETABLE_ID)
    }

    @After
    fun tearDown() {
        TimetablePrefs.setCurrent(ApplicationProvider.getApplicationContext(), null)
        Dispatchers.resetMain()
    }

    private fun showCourseManager(vararg courses: Course) {
        repo = FakeCourseRepo(courses.toList())
        val viewModel = CourseManagerViewModel(repo, FakeTimetableRepo())
        composeRule.setContent {
            MaterialTheme {
                CourseManagerScreen(
                    onBack = { },
                    onOpenSkipCourses = { },
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openDeleteDialog(course: Course) {
        composeRule.onNodeWithText(course.name).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun longPressCardOpensConfirmationAndCancelDeletesNothing() {
        showCourseManager(COURSE_A, COURSE_B)
        composeRule.onNodeWithTag(
            "course_card_weeks_${COURSE_A.id}",
            useUnmergedTree = true,
        ).assertIsDisplayed()

        openDeleteDialog(COURSE_A)
        composeRule.onNodeWithText(DELETE_TITLE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(
            "课程：" + COURSE_A.name,
            substring = true,
            useUnmergedTree = true,
        ).assertExists()

        composeRule.onNodeWithText("取消", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(0, repo.deletedCourseIds.size)
        assertEquals(0, repo.clearSemesterCalls)
        composeRule.onNodeWithText(COURSE_A.name).assertIsDisplayed()
        composeRule.onNodeWithText(COURSE_B.name).assertIsDisplayed()
    }

    @Test
    fun confirmedDeleteRemovesOnlyThePressedCourse() {
        showCourseManager(COURSE_A, COURSE_B)

        openDeleteDialog(COURSE_B)
        composeRule.onNodeWithText("删除", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(COURSE_B.id), repo.deletedCourseIds)
        composeRule.onNodeWithText(DELETE_TITLE, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(COURSE_A.name).assertIsDisplayed()
    }

    @Test
    fun confirmationIsScopedToTheCourseUnderTheFinger() {
        showCourseManager(COURSE_A, COURSE_B)

        openDeleteDialog(COURSE_A)
        composeRule.onNodeWithText(
            "课程：" + COURSE_A.name,
            substring = true,
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithText(
            "课程：" + COURSE_B.name,
            substring = true,
            useUnmergedTree = true,
        ).assertDoesNotExist()

        composeRule.onNodeWithText("删除", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(COURSE_A.id), repo.deletedCourseIds)
        composeRule.onNodeWithText(COURSE_B.name).assertIsDisplayed()
    }

    @Test
    fun clearAllCoursesRequiresConfirmationAndCancelClearsNothing() {
        showCourseManager(COURSE_A, COURSE_B)

        composeRule.onNodeWithText("清空").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(CLEAR_TITLE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(CLEAR_COPY, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("取消", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(0, repo.clearSemesterCalls)
        assertEquals(0, repo.deletedCourseIds.size)
        composeRule.onNodeWithText(COURSE_A.name).assertIsDisplayed()
        composeRule.onNodeWithText(COURSE_B.name).assertIsDisplayed()
    }

    @Test
    fun emptyListStillExplainsHowToAddCoursesWithoutAnyDestructiveAction() {
        showCourseManager()

        composeRule.onNodeWithText("暂无课程", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加课程").assertIsDisplayed()
        composeRule.onNodeWithText("清空").assertIsNotEnabled()
        composeRule.onNodeWithText("翘课设置").assertIsNotEnabled()
    }

    @Test
    fun editSheetScrollsLongNotesAndKeepsAccessibleColorChoicesAndSaveAction() {
        val courseWithLongNote = COURSE_A.copy(note = "长备注".repeat(80))
        showCourseManager(courseWithLongNote)

        composeRule.onNodeWithText(courseWithLongNote.name).performClick()
        val scrollContent = composeRule.onNodeWithTag("course_edit_scroll_content")
        scrollContent.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        scrollContent.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val selectedColor = composeRule.onNodeWithContentDescription("课程颜色：珊瑚橙")
        selectedColor.assertIsDisplayed().assertHasClickAction().assertIsSelected()

        val yellow = composeRule.onNodeWithContentDescription("课程颜色：金黄色")
        yellow.assertIsDisplayed().assertHasClickAction().assertIsNotSelected()

        scrollContent.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("course_edit_save")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
    }

    private companion object {
        const val TIMETABLE_ID = 5L
        const val SEMESTER_ID = 7L
        const val DELETE_TITLE = "删除课程？"
        const val CLEAR_TITLE = "清空全部课程？"
        const val CLEAR_COPY = "将删除当前课表的全部课程，此操作不可撤销。"

        val COURSE_A = Course(
            id = 11L,
            semesterId = SEMESTER_ID,
            name = "高等数学A",
            teacher = "王老师",
            location = "上庚",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = setOf(1, 2),
            source = CourseSource.MANUAL,
        )

        val COURSE_B = Course(
            id = 22L,
            semesterId = SEMESTER_ID,
            name = "高级语言程序设计",
            teacher = "李老师",
            location = "海韵",
            dayOfWeek = 3,
            startSection = 3,
            duration = 2,
            weeks = setOf(1, 2),
            source = CourseSource.IMPORT,
        )
    }

    private class FakeTimetableRepo : TimetableObservationContract {
        private val timetable = MutableStateFlow<Timetable?>(
            Timetable(id = TIMETABLE_ID, name = "2026-2027 秋季", semesterId = SEMESTER_ID),
        )

        override fun observeTimetable(id: Long): Flow<Timetable?> =
            if (id == TIMETABLE_ID) timetable else MutableStateFlow(null)
    }

    private class FakeCourseRepo(initial: List<Course>) : CourseManagementContract {
        val courses = MutableStateFlow(initial)
        val deletedCourseIds = mutableListOf<Long>()
        var clearSemesterCalls = 0
        var lastClearedSemesterId = -1L

        private val skipped = MutableStateFlow<Set<Long>>(emptySet())

        override fun observeCoursesByTimetable(timetableId: Long): Flow<List<Course>> = courses

        override fun observeSkippedCourseIds(): Flow<Set<Long>> = skipped

        override suspend fun addCourse(semesterId: Long, course: Course) = Unit

        override suspend fun updateCourseName(courseId: Long, name: String) = Unit

        override suspend fun updateCourseTeacher(courseId: Long, teacher: String) = Unit

        override suspend fun updateCourseLocation(courseId: Long, location: String) = Unit

        override suspend fun updateCourseNote(courseId: Long, note: String) = Unit

        override suspend fun updateCourseColor(courseId: Long, color: String) = Unit

        override suspend fun saveSkippedCourses(courseIds: Collection<Long>) {
            skipped.value = courseIds.toSet()
        }

        override suspend fun deleteSemesterCourses(semesterId: Long) {
            clearSemesterCalls++
            lastClearedSemesterId = semesterId
            courses.value = emptyList()
        }

        override suspend fun deleteCourse(courseId: Long) {
            deletedCourseIds += courseId
            courses.value = courses.value.filterNot { it.id == courseId }
        }
    }
}
