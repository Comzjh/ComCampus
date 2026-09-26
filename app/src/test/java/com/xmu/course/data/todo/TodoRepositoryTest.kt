package com.xmu.course.data.todo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.CourseEntity
import com.xmu.course.data.local.SemesterEntity
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.data.tronclass.model.TronCourseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoRepositoryTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun `排序为未完成优先截止时间升序无截止时间最后`() = runTest {
        val repo = repository(clock = { 99L })
        val completed = TodoEntity(3L, "已完成", "", null, TodoSource.LOCAL.name, 1L, true, 1L, 3L)
        val noDeadline = TodoEntity(4L, "无截止时间", "", null, TodoSource.LOCAL.name, null, false, 1L, 4L)
        val later = TodoEntity(2L, "较晚", "", null, TodoSource.LOCAL.name, 20L, false, 1L, 2L)
        val sooner = TodoEntity(1L, "较早", "", null, TodoSource.LOCAL.name, 10L, false, 1L, 1L)
        listOf(completed, noDeadline, later, sooner).forEach { db.todoDao().insert(it) }

        assertEquals(listOf(sooner, later, noDeadline, completed), repo.observeTodos().first())
    }

    @Test
    fun `新增待办会转换标题并写入固定时间`() = runTest {
        val repo = repository(clock = { 123L })

        val id = repo.addTodo("  读书  ", "  章节一 ", null, null, TodoSource.TRONCLASS)

        val saved = db.todoDao().getById(id)!!
        assertEquals("读书", saved.title)
        assertEquals("章节一", saved.description)
        assertNull(saved.courseId)
        assertEquals(TodoSource.LOCAL.name, saved.source)
        assertEquals(123L, saved.createdTime)
        assertEquals(123L, saved.updatedTime)
    }

    @Test
    fun `课程目录按source区分本地和畅课ID`() = runTest {
        db.semesterDao().insert(SemesterEntity(id = 1L, code = "20261", name = "秋季"))
        db.courseDao().insert(
            CourseEntity(
                semesterId = 1L, name = "本地课程", teacher = "本地教师", location = "",
                dayOfWeek = 1, startSection = 1, duration = 2, weeks = "1", source = "IMPORT",
                color = "#fff", note = "",
            ),
        )
        db.tronCourseDao().insertAll(
            listOf(TronCourseEntity(tronCourseId = 9001L, name = "畅课课程", semester = "秋季", instructor = "畅课教师", updatedTime = 1L)),
        )

        val options = repository().getCourseOptions()

        assertEquals(setOf(TodoSource.LOCAL, TodoSource.TRONCLASS), options.map { it.source }.toSet())
        assertEquals(setOf(1L, 9001L), options.map { it.id }.toSet())
    }

    @Test
    fun `逾期状态随now动态计算且已完成不逾期`() {
        val dueAt = 2_000L
        val todo = TodoEntity(
            title = "动态逾期", description = "", courseId = null,
            source = TodoSource.TRONCLASS.name, deadline = dueAt,
            completed = false, createdTime = 1L, updatedTime = 1L,
        )

        assertFalse(TodoRepository.isOverdue(todo, dueAt - 1))
        assertTrue(TodoRepository.isOverdue(todo, dueAt))
        assertTrue(TodoRepository.isOverdue(todo, dueAt + 1))
        assertFalse(TodoRepository.isOverdue(todo.copy(completed = true), dueAt + 1))
    }

    private fun repository(clock: () -> Long = { 1L }) = TodoRepository(
        todoDao = db.todoDao(),
        courseDao = db.courseDao(),
        tronCourseDao = db.tronCourseDao(),
        clock = clock,
    )
}
