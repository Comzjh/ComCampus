package com.xmu.course.ui.settings

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsDatePickerTest {
    @Test
    fun savedStartDateIsPreselectedAtUtcMidnight() {
        val expected = LocalDate.parse("2026-09-07")
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, startDatePickerMillis("2026-09-07"))
    }

    @Test
    fun missingOrInvalidStartDateLeavesPickerUnselected() {
        assertNull(startDatePickerMillis(null))
        assertNull(startDatePickerMillis("not-a-date"))
    }
}
