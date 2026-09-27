package com.xmu.course.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.ViewWeekPreference
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.domain.Course
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.timetable.TimetableUiState
import com.xmu.course.ui.timetable.TimetableViewModel
import com.xmu.course.ui.todo.TodoState
import com.xmu.course.ui.todo.TodoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** 固定测试时钟：2026-09-18 为星期五；课表 startDate=2026-09-14（星期一，第 1 教学周）。 */
private fun fridayClock(hour: Int, minute: Int) = HomeClock(LocalDateTime.of(2026, 9, 18, hour, minute))

private fun HomeClock.epochMillis(): Long =
    dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private val homeCourses = listOf(
    Course(id = 101L, semesterId = 1L, name = "高等数学", location = "上弦场", dayOfWeek = 5, startSection = 1, duration = 2, weeks = (1..25).toSet()),
    Course(id = 102L, semesterId = 1L, name = "大学英语", dayOfWeek = 5, startSection = 3, duration = 2, weeks = (1..25).toSet()),
    Course(id = 103L, semesterId = 1L, name = "周一课", dayOfWeek = 1, startSection = 1, duration = 2, weeks = (1..25).toSet()),
)

private fun fridayTimetable(): Timetable = Timetable(
    id = 1L,
    name = "2026-2027 秋冬",
    semesterId = 1L,
    startDate = "2026-09-14",
    totalWeeks = 25,
)

private fun fridayUiState(skipped: Set<Long> = emptySet()) = TimetableUiState(
    timetable = fridayTimetable(),
    courses = homeCourses,
    skippedCourseIds = skipped,
    actualWeek = 1,
    isLoading = false,
    hasData = true,
)

private fun fridayFeatureState(skipped: Set<Long> = emptySet()) = TimetableFeatureState(
    timetable = fridayTimetable(),
    courses = homeCourses,
    skippedCourseIds = skipped,
)

private fun todoItem(id: Long, title: String, deadline: Long?, completed: Boolean) = TodoFeatureModel(
    id = TodoFeatureId(id),
    title = title,
    description = "",
    courseReference = null,
    source = TodoFeatureSource.LOCAL,
    deadline = deadline,
    completed = completed,
)

/** 首页派生逻辑（纯函数）测试：缺失数据一律按空处理，不允许任何猜测性补全。 */
class HomeScreenDerivedTest {
    @Test
    fun `date label formats month day and chinese weekday`() {
        assertEquals("9月18日 星期五", homeDateLabel(fridayClock(7, 30)))
    }

    @Test
    fun `dashboard exposes next course and remaining list before classes`() {
        val dashboard = resolveHomeDashboard(fridayUiState(), TodoState.Empty(), fridayClock(7, 30))
        val snapshot = dashboard.today

        assertEquals(listOf("高等数学", "大学英语"), snapshot.courses.map { it.name })
        assertNull(snapshot.current)
        assertEquals("高等数学", snapshot.next?.name)
        assertEquals("还有30分钟", snapshot.countdown)
        assertEquals(listOf("大学英语"), snapshot.remaining.map { it.name })
    }

    @Test
    fun `dashboard separates current course from next course`() {
        val dashboard = resolveHomeDashboard(fridayUiState(), TodoState.Empty(), fridayClock(8, 20))
        val snapshot = dashboard.today

        assertEquals("高等数学", snapshot.current?.name)
        assertEquals("大学英语", snapshot.next?.name)
        assertEquals("正在上课", snapshot.countdown)
        assertEquals(emptyList<String>(), snapshot.remaining.map { it.name })
    }

    @Test
    fun `after last class there is no next course`() {
        val snapshot = resolveHomeDashboard(fridayUiState(), TodoState.Empty(), fridayClock(20, 0)).today

        assertNull(snapshot.current)
        assertNull(snapshot.next)
        assertEquals("今日无课程", snapshot.countdown)
        assertEquals(emptyList<String>(), snapshot.remaining.map { it.name })
    }

    @Test
    fun `skipped course is excluded from today snapshot`() {
        val snapshot = resolveHomeDashboard(
            fridayUiState(skipped = setOf(101L)),
            TodoState.Empty(),
            fridayClock(7, 30),
        ).today

        assertEquals(listOf("大学英语"), snapshot.courses.map { it.name })
        assertEquals("大学英语", snapshot.next?.name)
        assertEquals("还有2小时40分钟", snapshot.countdown)
    }

    @Test
    fun `dashboard todos keep only urgent unfinished items and cap at three`() {
        val clock = fridayClock(7, 30)
        val now = clock.epochMillis()
        val state = TodoState.Success(
            todos = listOf(
                todoItem(1, "逾期", now - 1_000L, completed = false),
                todoItem(2, "截止时刻", now, completed = false),
                todoItem(3, "截止后一毫秒", now + 1L, completed = false),
                todoItem(4, "今天", now + 60_000L, completed = false),
                todoItem(5, "明天", now + 24 * 60 * 60 * 1_000L, completed = false),
                todoItem(6, "三天内", now + 2 * 24 * 60 * 60 * 1_000L, completed = false),
                todoItem(7, "远期", now + 5 * 24 * 60 * 60 * 1_000L, completed = false),
                todoItem(8, "无截止", null, completed = false),
                todoItem(9, "已完成", now - 2_000L, completed = true),
            ),
        )

        val dashboard = resolveHomeDashboard(fridayUiState(), state, clock)

        assertEquals(listOf("逾期", "截止时刻", "截止后一毫秒"), dashboard.urgentTodos.map { it.title })
        assertEquals(6, dashboard.urgentTodoCount)
        assertEquals(HomeTodoAvailability.AVAILABLE, dashboard.todoAvailability)
    }

    @Test
    fun `todo error keeps cached urgent data and reports partial availability`() {
        val clock = fridayClock(7, 30)
        val state = TodoState.Error(
            message = "boom",
            todos = listOf(todoItem(1, "缓存待办", clock.epochMillis() + 60_000L, completed = false)),
        )

        val dashboard = resolveHomeDashboard(fridayUiState(), state, clock)

        assertEquals(listOf("缓存待办"), dashboard.urgentTodos.map { it.title })
        assertNull(dashboard.urgentTodoCount)
        assertEquals(HomeTodoAvailability.PARTIAL_ERROR, dashboard.todoAvailability)
    }
}

/** 首页组合测试：只验证渲染与回调接线，数据全部来自既有 ViewModel 的 fake 注入。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun newTimetableViewModel(state: TimetableFeatureState): TimetableViewModel =
        TimetableViewModel(
            ApplicationProvider.getApplicationContext(),
            FakeTimetableRepository(state),
            FakeViewWeekPreference(),
        )

    @Test
    fun homeScreenPrioritizesNextCourseAndUrgentTodos() {
        var timetableClicked = 0
        var todoClicked = 0
        var addTodoClicked = 0
        var importClicked = 0
        val clock = fridayClock(7, 30)
        val now = clock.epochMillis()

        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    timetableViewModel = newTimetableViewModel(fridayFeatureState()),
                    todoViewModel = TodoViewModel(
                        FakeTodoRepository(
                            listOf(
                                todoItem(1, "复习", now + 60_000L, completed = false),
                                todoItem(2, "交实验报告", now + 2 * 60 * 60 * 1_000L, completed = false),
                                todoItem(3, "已完成事项", null, completed = true),
                            ),
                        ),
                    ),
                    onOpenTimetable = { timetableClicked++ },
                    onOpenTodo = { todoClicked++ },
                    onAddTodo = { addTodoClicked++ },
                    onOpenImport = { importClicked++ },
                    clock = clock,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_screen").assertExists()
        composeRule.onNodeWithText("9月18日 星期五").assertExists()
        composeRule.onNodeWithTag("home_next_class").assertExists()
        composeRule.onNodeWithText("还有30分钟").assertExists()
        composeRule.onNodeWithText("高等数学").assertExists()
        composeRule.onNodeWithText("大学英语").assertExists()
        composeRule.onNodeWithText("2 项需要注意").performScrollTo().assertExists()
        composeRule.onNodeWithText("复习").performScrollTo().assertExists()
        composeRule.onNodeWithTag("home_todo_deadline_1").performScrollTo().assertExists()
        composeRule.onNodeWithText("还有1m").performScrollTo().assertExists()
        composeRule.onNodeWithTag("home_gpa_summary").assertDoesNotExist()
        composeRule.onNodeWithTag("home_quick_actions").assertDoesNotExist()

        composeRule.onNodeWithTag("home_open_timetable").performScrollTo().performClick()
        composeRule.onNodeWithTag("home_add_todo").performScrollTo().performClick()
        composeRule.onNodeWithTag("home_open_todo").performScrollTo().performClick()

        assertEquals(1, timetableClicked)
        assertEquals(1, addTodoClicked)
        assertEquals(1, todoClicked)
        assertEquals(0, importClicked)
    }

    @Test
    fun homeScreenShowsImportEmptyStateWhenTimetableDataIsMissing() {
        var importClicked = 0

        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    timetableViewModel = newTimetableViewModel(TimetableFeatureState()),
                    todoViewModel = TodoViewModel(FakeTodoRepository(emptyList())),
                    onOpenTimetable = {},
                    onOpenTodo = {},
                    onAddTodo = {},
                    onOpenImport = { importClicked++ },
                    clock = fridayClock(8, 0),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_today_empty").performScrollTo().assertExists()
        composeRule.onNodeWithText("还没有课表数据").performScrollTo().assertExists()
        composeRule.onNodeWithText("导入课表").performScrollTo().performClick()
        assertEquals(1, importClicked)
        composeRule.onNodeWithText("今天没有需要处理的事项").performScrollTo().assertExists()
    }

    @Test
    fun todayCardHidesNextClassAfterLastPeriod() {
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    timetableViewModel = newTimetableViewModel(fridayFeatureState()),
                    todoViewModel = TodoViewModel(FakeTodoRepository(listOf(todoItem(1, "复习", 100L, completed = false)))),
                    onOpenTimetable = {},
                    onOpenTodo = {},
                    onAddTodo = {},
                    onOpenImport = {},
                    clock = fridayClock(20, 0),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_next_class").assertDoesNotExist()
        composeRule.onNodeWithText("今天课程已结束").assertExists()
    }
}

private class FakeTimetableRepository(
    initial: TimetableFeatureState,
) : TimetableFeatureRepository {
    private val state = MutableStateFlow(initial)

    override fun observeCurrentTimetableState(): Flow<TimetableFeatureState> = state

    override suspend fun addCourse(course: Course) = Unit

    override suspend fun updateCourseColor(courseId: Long, color: String) = Unit

    override suspend fun updateCourseNote(courseId: Long, note: String) = Unit

    override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) = Unit

    override suspend fun updateConfig(config: TimetableConfig) = Unit
}

private class FakeViewWeekPreference : ViewWeekPreference {
    override fun getViewWeek(timetableId: Long): Int? = null

    override fun setViewWeek(timetableId: Long, week: Int) = Unit
}

private class FakeTodoRepository(
    private val todos: List<TodoFeatureModel>,
) : TodoFeatureRepository {
    override fun observeTodos(): Flow<List<TodoFeatureModel>> = flowOf(todos)

    override suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId = TodoFeatureId(0L)

    override suspend fun updateTodo(command: EditTodoFeatureCommand) = Unit

    override suspend fun setCompleted(id: TodoFeatureId, completed: Boolean) = Unit

    override suspend fun deleteTodo(id: TodoFeatureId) = Unit

    override suspend fun getCourseOptions(): List<TodoCourseOptionModel> = emptyList()
}
