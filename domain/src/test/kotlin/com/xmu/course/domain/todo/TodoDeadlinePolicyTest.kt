package com.xmu.course.domain.todo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoDeadlinePolicyTest {
    @Test
    fun `deadline boundary is inclusive at millisecond precision`() {
        val now = 1_800_000_000_000L

        assertTrue(TodoDeadlinePolicy.isOverdue(now - 1L, now))
        assertTrue(TodoDeadlinePolicy.isOverdue(now, now))
        assertFalse(TodoDeadlinePolicy.isOverdue(now + 1L, now))
        assertFalse(TodoDeadlinePolicy.isOverdue(null, now))
    }
}
