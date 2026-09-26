package com.xmu.course

import com.xmu.course.domain.isValidFirstWeekStartDate
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportStartDatePolicyTest {
    @Test
    fun `开学日期必须是星期一`() {
        assertTrue(isValidFirstWeekStartDate(LocalDate.of(2026, 9, 14)))
        assertFalse(isValidFirstWeekStartDate(LocalDate.of(2026, 9, 15)))
    }

    @Test
    fun `规则覆盖完整一周`() {
        val monday = LocalDate.of(2026, 9, 14)
        assertTrue(isValidFirstWeekStartDate(monday))
        assertFalse(isValidFirstWeekStartDate(monday.with(DayOfWeek.SUNDAY)))
    }
}
