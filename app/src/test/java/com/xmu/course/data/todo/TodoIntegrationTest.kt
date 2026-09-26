package com.xmu.course.data.todo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.tronclass.assignment.AssignmentDto
import com.xmu.course.data.tronclass.assignment.AssignmentPageDto
import com.xmu.course.data.tronclass.assignment.AssignmentParser
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoIntegrationTest {
    private lateinit var db: com.xmu.course.data.local.AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            com.xmu.course.data.local.AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun `Fake Assignment经过DTO Parser生成Todo并写入Room`() = runTest {
        val parsed = AssignmentParser(ZoneOffset.UTC).parse(
            AssignmentPageDto(
                homeworkActivities = listOf(
                    AssignmentDto(
                        id = 123L,
                        title = "测试作业",
                        description = "测试描述",
                        courseId = 9001L,
                        dueAt = "2026-09-20T23:59:00Z",
                        endTime = null,
                    ),
                ),
                pages = 1,
                page = 1,
                pageSize = 100,
            ),
        )

        val assignment = (parsed as TronResult.Success).value.assignments.single()
        val todo = TodoEntity(
            title = assignment.title,
            description = assignment.description,
            courseId = assignment.remoteCourseId,
            source = TodoSource.TRONCLASS.name,
            deadline = assignment.deadline,
            createdTime = 1L,
            updatedTime = 1L,
            externalId = assignment.externalId,
        )
        db.todoDao().insert(todo)

        val saved = db.todoDao().getAll().single()
        assertEquals("123", saved.externalId)
        assertEquals("测试作业", saved.title)
        assertEquals(9001L, saved.courseId)
        assertEquals(TodoSource.TRONCLASS.name, saved.source)
    }
}
