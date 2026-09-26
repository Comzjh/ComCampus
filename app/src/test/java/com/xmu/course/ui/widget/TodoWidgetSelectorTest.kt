package com.xmu.course.ui.widget

import com.xmu.course.data.todo.model.TodoSource
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoWidgetSelectorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun todo(
        title: String,
        deadline: Long?,
        completed: Boolean = false,
        source: TodoSource = TodoSource.LOCAL,
        externalId: String? = null,
        updatedTime: Long = now,
    ) = WidgetTodoRecord(
        title = title,
        id = 0L,
        courseId = null,
        source = when (source) {
            TodoSource.LOCAL -> WidgetTodoSource.LOCAL
            TodoSource.TRONCLASS -> WidgetTodoSource.TRONCLASS
        },
        completed = completed,
        updatedTime = updatedTime,
        deadline = deadline,
    )

    @Test
    fun completedTodosAreExcludedAndUnfinishedCountIsPreserved() {
        val result = WidgetRepository.selectTodosForWidget(
            listOf(
                todo("已完成", now - 1_000, completed = true),
                todo("本地", now + 1_000),
                todo("作业", now + 2_000, source = TodoSource.TRONCLASS, externalId = "homework-1"),
                todo("日常练习", now + 3_000, source = TodoSource.TRONCLASS, externalId = "exam:1"),
            ),
            nowMillis = now,
        )

        assertEquals(3, result.totalUnfinished)
        assertEquals(listOf("本地", "作业", "日常练习"), result.items.map { it.title })
        assertFalse(result.items.any { it.title == "已完成" })
    }

    @Test
    fun deadlinesSortAscendingAndNullDeadlineIsLast() {
        val result = WidgetRepository.selectTodosForWidget(
            listOf(
                todo("无截止", null),
                todo("较晚", now + 20_000),
                todo("较早", now + 10_000),
            ),
            nowMillis = now,
        )

        assertEquals(listOf("较早", "较晚", "无截止"), result.items.map { it.title })
        assertNull(result.items.last().deadline)
    }

    @Test
    fun widgetShowsAtMostFourButHeaderKeepsTotal() {
        val result = WidgetRepository.selectTodosForWidget(
            (1..10).map { index -> todo("待办$index", now + index * 1_000) },
            nowMillis = now,
            maxDisplay = 4,
        )

        assertEquals(10, result.totalUnfinished)
        assertEquals(4, result.items.size)
        assertEquals(listOf("待办1", "待办2", "待办3", "待办4"), result.items.map { it.title })
    }

    @Test
    fun overdueUsesRuntimeClockAndCompletedItemIsNotSelected() {
        val result = WidgetRepository.selectTodosForWidget(
            listOf(
                todo("截止前一毫秒", now - 1),
                todo("截止时刻", now),
                todo("截止后一毫秒", now + 1),
                todo("已完成逾期", now - 2_000, completed = true),
            ),
            nowMillis = now,
        )

        assertEquals(3, result.totalUnfinished)
        assertEquals(listOf(true, true, false), result.items.map { it.isOverdue })
    }

    @Test
    fun deadlineFormatterUsesLocalDayLabelsAndMinutePrecision() {
        val today = ZonedDateTime.of(2026, 9, 14, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val tomorrow = ZonedDateTime.of(2026, 9, 15, 8, 5, 0, 0, zone).toInstant().toEpochMilli()
        val future = ZonedDateTime.of(2026, 10, 1, 9, 7, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("今天 23:59", WidgetRepository.formatTodoDeadline(today, now, zone))
        assertEquals("明天 08:05", WidgetRepository.formatTodoDeadline(tomorrow, now, zone))
        assertEquals("10月1日 09:07", WidgetRepository.formatTodoDeadline(future, now, zone))
        assertEquals("无截止时间", WidgetRepository.formatTodoDeadline(null, now, zone))
    }

    @Test
    fun instantDeadlineFormattingDoesNotDependOnSystemTimezone() {
        val deadline = Instant.parse("2026-09-14T15:30:00Z").toEpochMilli()

        assertEquals("今天 23:30", WidgetRepository.formatTodoDeadline(deadline, now, zone))
    }

    @Test
    fun xmuDeadlineAtUtc1559DisplaysChinaLocal2359() {
        val deadline = Instant.parse("2026-09-20T15:59:00Z").toEpochMilli()

        assertEquals("9月20日 23:59", WidgetRepository.formatTodoDeadline(deadline, now, zone))
    }

    @Test
    fun longTextUsesDisplayEllipsisWithoutChangingStoredValue() {
        assertEquals("abcdef…", WidgetRepository.truncateWidgetText("abcdefghi", 7))
        assertEquals("短标题", WidgetRepository.truncateWidgetText("短标题", 7))
    }
}
