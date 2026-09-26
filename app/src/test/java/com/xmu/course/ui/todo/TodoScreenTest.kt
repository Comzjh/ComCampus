package com.xmu.course.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.domain.todo.TodoDatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        viewModel = TodoViewModel(repository)
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    @Test
    fun `空状态显示暂无待办并可打开创建表单`() {
        setContent()

        composeRule.onNodeWithText("暂无待办").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新增待办").performClick()
        composeRule.onNodeWithText("新增待办").assertIsDisplayed()
        composeRule.onNodeWithText("标题").assertIsDisplayed()
    }

    @Test
    fun `空态主操作直接打开本地创建表单`() {
        setContent()
        composeRule.onNodeWithText("添加待办").performClick()
        composeRule.onNodeWithText("新增待办").assertIsDisplayed()
        composeRule.onNodeWithText("标题").assertIsDisplayed()
    }

    @Test
    fun `创建待办`() {
        setContent()
        composeRule.onNodeWithContentDescription("新增待办").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("准备考试")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("准备考试").assertIsDisplayed()
    }

    @Test
    fun `完成和删除待办`() {
        repository.todos.value = listOf(sampleTodo())
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithTag("todo-checkbox").performClick()
        assertTrue(repository.lastCompleted)
        composeRule.onNodeWithContentDescription("待办操作").performClick()
        composeRule.onNodeWithText("删除待办").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("暂无待办").assertIsDisplayed()
    }

    @Test
    fun `畅课待办显示来源和关联课程`() {
        repository.todos.value = listOf(
            sampleTodo().copy(
                title = "1-1 数据类型二",
                courseReference = TodoCourseReference.External(9001L),
                source = TodoFeatureSource.EXTERNAL,
            ),
        )
        repository.options = listOf(
            TodoCourseOptionModel(TodoCourseReference.External(9001L), "Python程序设计", "教师"),
        )
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithText("1-1 数据类型二").assertIsDisplayed()
        composeRule.onNodeWithText("Python程序设计 · 来自畅课").assertIsDisplayed()
        composeRule.onNodeWithText("截止").assertDoesNotExist()
    }

    @Test
    fun `默认显示总待办并显示截止小时分钟`() {
        val deadline = LocalDateTime.of(2099, 9, 20, 23, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        repository.todos.value = listOf(sampleTodo().copy(title = "提前预看", deadline = deadline))
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithText("待办").assertIsDisplayed()
        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("提前预看").assertIsDisplayed()
        composeRule.onNodeWithText("截止 2099-09-20 23:59").assertIsDisplayed()
    }

    @Test
    fun `待办完成控件提供勾选状态和48dp触控区域`() {
        repository.todos.value = listOf(sampleTodo())
        viewModel = TodoViewModel(repository)
        setContent()

        val checkbox = composeRule.onNodeWithTag("todo-checkbox")
        checkbox.assertIsOff()
        checkbox.assertWidthIsAtLeast(48.dp)
        checkbox.assertHeightIsAtLeast(48.dp)
        checkbox.performClick()
        checkbox.assertIsOn()
    }

    @Test
    fun `新建日期截止显示为所选本地日结束`() {
        val date = LocalDate.of(2099, 9, 20)
        val selectedDate = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val deadline = TodoDatePolicy.dateOnlyDeadline(selectedDate)
        repository.todos.value = listOf(sampleTodo().copy(title = "日期任务", deadline = deadline))
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithText("截止 2099-09-20（当天结束）").assertIsDisplayed()
    }

    @Test
    fun `旧版日期待办不再显示成上午八点`() {
        val legacyDateMillis = LocalDate.of(2099, 9, 20)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        repository.todos.value = listOf(
            sampleTodo().copy(title = "旧版日期任务", deadline = legacyDateMillis, source = TodoFeatureSource.LOCAL),
        )
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithText("截止 2099-09-20（当天结束）").assertIsDisplayed()
    }

    @Test
    fun `不改日期直接保存编辑保留原截止时分`() {
        val deadline = LocalDateTime.of(2099, 9, 20, 13, 45)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        repository.todos.value = listOf(sampleTodo().copy(title = "保留时间", deadline = deadline))
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithContentDescription("待办操作").performClick()
        composeRule.onNodeWithText("编辑待办").performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitForIdle()

        assertEquals(deadline, repository.lastUpdated?.deadline)
    }

    @Test
    fun `长描述下编辑表单可滚到清除截止日期且保存仍可达`() {
        val longDescription = "长描述内容用于检查滚动布局。".repeat(120)
        val deadline = LocalDateTime.of(2099, 9, 20, 13, 45)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        repository.todos.value = listOf(
            sampleTodo().copy(title = "长描述待办", description = longDescription, deadline = deadline),
        )
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithContentDescription("待办操作").performClick()
        composeRule.onNodeWithText("编辑待办").performClick()

        composeRule.onNodeWithTag("todo_editor_scroll_content")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithTag("todo_editor_clear_deadline")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("todo_editor_save").assertIsDisplayed().performClick()

        assertEquals(longDescription, repository.lastUpdated?.description)
        assertEquals(deadline, repository.lastUpdated?.deadline)
    }

    @Test
    fun `输入长描述后可滚到截止日期且取消仍可达`() {
        setContent()
        composeRule.onNodeWithContentDescription("新增待办").performClick()
        composeRule.onNodeWithTag("todo_editor_title").performTextInput("长描述任务")
        composeRule.onNodeWithTag("todo_editor_description")
            .performTextInput("这是一段用于滚动和键盘布局检查的描述。".repeat(100))

        composeRule.onNodeWithTag("todo_editor_scroll_content")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithTag("todo_editor_deadline")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("todo_editor_datepicker_confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("todo_editor_datepicker_cancel").performClick()

        composeRule.onNodeWithTag("todo_editor_save").assertIsDisplayed()
        composeRule.onNodeWithTag("todo_editor_cancel").assertIsDisplayed().performClick()
        assertTrue(repository.todos.value.isEmpty())
        assertEquals(null, repository.lastUpdated)
    }

    @Test
    fun `待办可以按截止时间范围提前预看`() {
        val now = System.currentTimeMillis()
        repository.todos.value = listOf(
            sampleTodo().copy(id = TodoFeatureId(1L), title = "一天内", deadline = now + 60 * 60 * 1000L),
            sampleTodo().copy(id = TodoFeatureId(2L), title = "三天内", deadline = now + 2 * 24 * 60 * 60 * 1000L),
            sampleTodo().copy(id = TodoFeatureId(3L), title = "七天内", deadline = now + 5 * 24 * 60 * 60 * 1000L),
            sampleTodo().copy(id = TodoFeatureId(4L), title = "长期任务", deadline = now + 10 * 24 * 60 * 60 * 1000L),
            sampleTodo().copy(id = TodoFeatureId(5L), title = "已逾期", deadline = now - 2 * 24 * 60 * 60 * 1000L),
        )
        viewModel = TodoViewModel(repository)
        setContent()

        composeRule.onNodeWithText("全部").assertIsDisplayed()

        composeRule.onNodeWithTag("todo_filter_overdue").performClick()
        composeRule.onNodeWithText("已逾期").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("一天内").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("1 天").performClick()
        assertTrue(composeRule.onAllNodesWithText("一天内").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("三天内").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("已逾期").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("长期任务").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("长期").performClick()
        composeRule.onNodeWithText("长期任务").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `待办页面显示畅课刷新按钮`() {
        setContent()

        composeRule.onNodeWithContentDescription("刷新畅课待办").assertIsDisplayed()
    }

    @Test
    fun `手动刷新成功显示同步数量`() {
        viewModel = TodoViewModel(
            repository = repository,
            refreshReader = TodoRefreshReader { TodoRefreshResult.Success(3) },
        )
        setContent()

        composeRule.onNodeWithContentDescription("刷新畅课待办").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("最近手动同步：已同步 3 项").assertIsDisplayed()
    }

    @Test
    fun `全部范围按每日紧迫度分组`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 9, 19, 12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val day = 24 * 60 * 60 * 1_000L
        val sections = groupTodosForDailyView(
            todos = listOf(
                sampleTodo().copy(id = TodoFeatureId(1), title = "逾期", deadline = now - 1),
                sampleTodo().copy(id = TodoFeatureId(2), title = "截止时刻", deadline = now),
                sampleTodo().copy(id = TodoFeatureId(3), title = "截止后一毫秒", deadline = now + 1),
                sampleTodo().copy(id = TodoFeatureId(4), title = "近期", deadline = now + 2 * day),
                sampleTodo().copy(id = TodoFeatureId(5), title = "以后", deadline = now + 10 * day),
                sampleTodo().copy(id = TodoFeatureId(6), title = "无截止", deadline = null),
                sampleTodo().copy(id = TodoFeatureId(7), title = "已完成", completed = true),
            ),
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(
            listOf("逾期", "今天", "近期", "以后", "无截止日期", "已完成"),
            sections.map { it.label },
        )
        assertEquals(listOf("逾期", "截止时刻"), sections.first().todos.map { it.title })
        assertEquals(listOf("截止后一毫秒"), sections[1].todos.map { it.title })
    }

    @Test
    fun `日期任务在同一天的定时任务之后排序`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 9, 20)
        val now = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val dateOnly = TodoDatePolicy.dateOnlyDeadline(
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val timed = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()

        val sections = groupTodosForDailyView(
            todos = listOf(
                sampleTodo().copy(id = TodoFeatureId(1), title = "整天任务", deadline = dateOnly),
                sampleTodo().copy(id = TodoFeatureId(2), title = "定时任务", deadline = timed),
            ),
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("今天", sections.single().label)
        assertEquals(listOf("定时任务", "整天任务"), sections.single().todos.map { it.title })
    }

    @Test
    fun `首页请求可以直接打开新增待办`() {
        var consumed = 0
        composeRule.setContent {
            MaterialTheme {
                TodoScreen(
                    viewModel = viewModel,
                    openEditorRequest = true,
                    onOpenEditorRequestConsumed = { consumed++ },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("新增待办").assertIsDisplayed()
        composeRule.onNodeWithText("标题").assertIsDisplayed()
        assertEquals(1, consumed)
    }

    private fun setContent() {
        composeRule.setContent {
            MaterialTheme { TodoScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()
    }

    private fun sampleTodo() = TodoFeatureModel(
        id = TodoFeatureId(1L), title = "测试待办", description = "", courseReference = null,
        source = TodoFeatureSource.LOCAL, deadline = null, completed = false,
    )

    private class FakeRepository : TodoFeatureRepository {
        val todos = MutableStateFlow<List<TodoFeatureModel>>(emptyList())
        var options = emptyList<TodoCourseOptionModel>()
        var lastCompleted = false
        var lastUpdated: EditTodoFeatureCommand? = null

        override fun observeTodos(): Flow<List<TodoFeatureModel>> = todos

        override suspend fun addTodo(command: CreateTodoFeatureCommand): TodoFeatureId {
            todos.value = listOf(sampleTodo().copy(title = command.title))
            return TodoFeatureId(1L)
        }

        override suspend fun updateTodo(command: EditTodoFeatureCommand) {
            lastUpdated = command
            todos.value = listOf(sampleTodo().copy(
                title = command.title,
                description = command.description,
                deadline = command.deadline,
            ))
        }

        override suspend fun setCompleted(id: TodoFeatureId, completed: Boolean) {
            lastCompleted = completed
            todos.value = listOf(sampleTodo().copy(completed = completed))
        }

        override suspend fun deleteTodo(id: TodoFeatureId) { todos.value = emptyList() }

        override suspend fun getCourseOptions(): List<TodoCourseOptionModel> = options

        private fun sampleTodo() = TodoFeatureModel(
            id = TodoFeatureId(1L), title = "测试待办", description = "", courseReference = null,
            source = TodoFeatureSource.LOCAL, deadline = null, completed = false,
        )
    }
}
