package com.xmu.course.domain.todo

/** Shared deadline boundary for persisted epoch-millisecond Todo timestamps. */
object TodoDeadlinePolicy {
    /** A deadline is overdue at the exact instant it is reached. */
    fun isOverdue(deadlineMillis: Long?, nowMillis: Long): Boolean =
        deadlineMillis != null && deadlineMillis <= nowMillis
}
