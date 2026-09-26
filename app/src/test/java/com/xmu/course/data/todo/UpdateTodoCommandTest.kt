package com.xmu.course.data.todo

import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTodoCommandTest {
    @Test
    fun `编辑命令只覆盖可编辑字段并保留持久化字段`() {
        val existing = TodoEntity(
            id = 7L,
            title = "旧标题",
            description = "旧描述",
            courseId = 12L,
            source = TodoSource.TRONCLASS.name,
            deadline = 100L,
            completed = true,
            createdTime = 11L,
            updatedTime = 12L,
            externalId = "homework-1",
        )
        val command = UpdateTodoCommand(
            id = 7L,
            title = "  新标题  ",
            description = "新描述",
            deadline = 200L,
            courseId = 15L,
            source = TodoSource.LOCAL,
        )

        val updated = TodoUpdateMapper.applyTo(existing, command, updatedTime = 99L)

        assertEquals("新标题", updated.title)
        assertEquals("新描述", updated.description)
        assertEquals(200L, updated.deadline)
        assertEquals(15L, updated.courseId)
        assertEquals(TodoSource.LOCAL.name, updated.source)
        assertTrue(updated.completed)
        assertEquals(11L, updated.createdTime)
        assertEquals("homework-1", updated.externalId)
        assertEquals(99L, updated.updatedTime)
    }
}
