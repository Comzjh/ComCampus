package com.xmu.course.data.todo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoUpdateMergeTest {
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
    fun `更新待办时保留完成状态创建时间和外部标识并刷新更新时间`() = runTest {
        val originalId = db.todoDao().insert(
            TodoEntity(
                title = "旧标题",
                description = "旧描述",
                courseId = 12L,
                source = TodoSource.TRONCLASS.name,
                deadline = 100L,
                completed = true,
                createdTime = 11L,
                updatedTime = 12L,
                externalId = "homework-1",
            ),
        )
        val original = db.todoDao().getById(originalId)!!
        val command = UpdateTodoCommand(
            id = original.id,
            title = "  新标题  ",
            description = "新描述",
            deadline = 200L,
            courseId = 15L,
            source = TodoSource.LOCAL,
        )

        TodoRepository(db.todoDao(), clock = { 99L }).updateTodo(command)

        val saved = db.todoDao().getById(originalId)!!
        assertEquals("新标题", saved.title)
        assertEquals("新描述", saved.description)
        assertEquals(15L, saved.courseId)
        assertEquals(TodoSource.LOCAL.name, saved.source)
        assertEquals(200L, saved.deadline)
        assertTrue(saved.completed)
        assertEquals(11L, saved.createdTime)
        assertEquals("homework-1", saved.externalId)
        assertEquals(99L, saved.updatedTime)
    }
}
