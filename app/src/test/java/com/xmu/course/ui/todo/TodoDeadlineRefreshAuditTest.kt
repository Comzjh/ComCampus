package com.xmu.course.ui.todo

import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Controls for time-derived state: advancing the clock needs no new database row. */
class TodoDeadlineRefreshAuditTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val deadline = LocalDate.of(2026, 10, 17)
        .atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `unchanged external todo moves to overdue at deadline without another sync`() {
        val unchangedRows = listOf(externalTodo(completed = false))

        assertEquals("今天", groupTodosForDailyView(unchangedRows, deadline - 1, zone).single().label)
        assertEquals("逾期", groupTodosForDailyView(unchangedRows, deadline, zone).single().label)
        assertEquals("逾期", groupTodosForDailyView(unchangedRows, deadline + 60_000, zone).single().label)
    }

    @Test
    fun `published completed state remains completed after its deadline`() {
        val publishedCompletedRows = listOf(externalTodo(completed = true))

        val sections = groupTodosForDailyView(publishedCompletedRows, deadline + 60_000, zone)

        assertEquals(listOf("已完成"), sections.map { it.label })
        assertEquals(publishedCompletedRows, sections.single().todos)
    }

    private fun externalTodo(completed: Boolean) = TodoFeatureModel(
        id = TodoFeatureId(1),
        title = "合成远端作业",
        description = "",
        courseReference = TodoCourseReference.External(101),
        source = TodoFeatureSource.EXTERNAL,
        deadline = deadline,
        completed = completed,
    )
}
