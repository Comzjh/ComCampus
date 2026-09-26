package com.xmu.course.ui.widget

import com.xmu.course.domain.todo.TodoDeadlinePolicy

/** 待办截止时间的纯展示结果；不修改 TodoEntity，也不依赖系统时钟。 */
data class TodoDeadlineDisplay(
    val text: String?,
    val urgency: TodoUrgency,
) {
    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        private const val DAY_MILLIS = 24 * HOUR_MILLIS

        fun resolve(deadlineMillis: Long?, nowMillis: Long): TodoDeadlineDisplay {
            if (deadlineMillis == null) return TodoDeadlineDisplay(text = null, urgency = TodoUrgency.NORMAL)

            if (TodoDeadlinePolicy.isOverdue(deadlineMillis, nowMillis)) {
                val elapsedMillis = nonNegativeDifference(nowMillis, deadlineMillis)
                return TodoDeadlineDisplay(
                    text = overdueText(elapsedMillis),
                    urgency = TodoUrgency.OVERDUE,
                )
            }

            val remainingMillis = nonNegativeDifference(deadlineMillis, nowMillis)
            val urgency = when {
                remainingMillis > 3 * DAY_MILLIS -> TodoUrgency.NORMAL
                remainingMillis > DAY_MILLIS -> TodoUrgency.UPCOMING
                remainingMillis > 6 * HOUR_MILLIS -> TodoUrgency.URGENT
                else -> TodoUrgency.CRITICAL
            }
            val text = when {
                remainingMillis > DAY_MILLIS -> "还有${ceilUnits(remainingMillis, DAY_MILLIS)}D"
                remainingMillis > 6 * HOUR_MILLIS -> "还有${ceilUnits(remainingMillis, HOUR_MILLIS)}H"
                remainingMillis >= HOUR_MILLIS -> "还有${ceilUnits(remainingMillis, HOUR_MILLIS)}H"
                else -> "还有${ceilUnits(remainingMillis, MINUTE_MILLIS)}m"
            }
            return TodoDeadlineDisplay(text = text, urgency = urgency)
        }

        private fun overdueText(elapsedMillis: Long): String = when {
            elapsedMillis < HOUR_MILLIS -> "已逾期${maxOf(1L, elapsedMillis / MINUTE_MILLIS)}m"
            elapsedMillis <= DAY_MILLIS -> "已逾期${maxOf(1L, elapsedMillis / HOUR_MILLIS)}H"
            else -> "已逾期${maxOf(1L, elapsedMillis / DAY_MILLIS)}D"
        }

        private fun ceilUnits(value: Long, unit: Long): Long =
            value / unit + if (value % unit == 0L) 0L else 1L

        private fun nonNegativeDifference(later: Long, earlier: Long): Long =
            runCatching { Math.subtractExact(later, earlier) }.getOrDefault(Long.MAX_VALUE)
    }
}

enum class TodoUrgency {
    NORMAL,
    UPCOMING,
    URGENT,
    CRITICAL,
    OVERDUE,
}
