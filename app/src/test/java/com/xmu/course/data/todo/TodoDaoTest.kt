package com.xmu.course.data.todo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.todo.model.TodoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoDaoTest {
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
    fun `新增后可以查询`() = runTest {
        val id = db.todoDao().insert(todo(title = "复习高数"))

        assertEquals(1L, id)
        assertEquals("复习高数", db.todoDao().getAll().single().title)
    }

    @Test
    fun `删除后记录消失`() = runTest {
        val id = db.todoDao().insert(todo(title = "待删除"))

        db.todoDao().deleteById(id)

        assertTrue(db.todoDao().getAll().isEmpty())
    }

    @Test
    fun `更新完成状态`() = runTest {
        val id = db.todoDao().insert(todo(title = "完成我"))
        val saved = db.todoDao().getById(id)!!

        db.todoDao().updateCompletedById(id, completed = true, updatedTime = 2L)

        assertTrue(db.todoDao().getById(id)!!.completed)
        assertEquals(2L, db.todoDao().getById(id)!!.updatedTime)
        assertEquals(saved.title, db.todoDao().getById(id)!!.title)
    }

    @Test
    fun `observeAll随数据库变化刷新`() = runTest {
        assertTrue(db.todoDao().observeAll().first().isEmpty())

        db.todoDao().insert(todo(title = "Flow 待办"))

        val current = db.todoDao().observeAll().first()
        assertEquals(1, current.size)
        assertFalse(current.single().completed)
    }

    private fun todo(id: Long = 0L, title: String): TodoEntity = TodoEntity(
        id = id,
        title = title,
        description = "",
        courseId = null,
        deadline = null,
        createdTime = 1L,
        updatedTime = 1L,
    )
}
