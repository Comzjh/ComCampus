package com.xmu.course.domain.todo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodoDatePolicyTest {
    @Test
    fun `picker uses local calendar date for positive and negative offsets`() {
        val localDate = LocalDate.of(2026, 9, 20)
        listOf("Asia/Shanghai", "America/Los_Angeles", "UTC").forEach { zoneName ->
            val zone = ZoneId.of(zoneName)
            val deadline = localDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
            val pickerDate = TodoDatePolicy.pickerDateMillis(deadline, zone)

            assertEquals(
                localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                pickerDate,
                zoneName,
            )
        }
    }

    @Test
    fun `new date only deadline is the last millisecond of selected local date`() {
        val date = LocalDate.of(2026, 9, 20)
        val selected = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val storedDate = TodoDatePolicy.dateOnlyDeadline(selected)
        listOf("Asia/Shanghai", "America/Los_Angeles", "America/New_York").forEach { zoneName ->
            val zone = ZoneId.of(zoneName)
            val deadline = TodoDatePolicy.effectiveDeadline(storedDate, zone, isDateOnly = true)
            val local = Instant.ofEpochMilli(deadline).atZone(zone)

            assertEquals(date, local.toLocalDate(), zoneName)
            assertEquals(LocalTime.of(23, 59, 59, 999_000_000), local.toLocalTime(), zoneName)
            assertTrue(TodoDatePolicy.isEndOfLocalDay(deadline, zone), zoneName)
            assertEquals(storedDate, TodoDatePolicy.dateOnlyDeadline(selected), "date sentinel must not move when the device zone changes")
        }
    }

    @Test
    fun `legacy utc midnight task remains a date and resolves to local end of selected date`() {
        val date = LocalDate.of(2026, 9, 20)
        val legacy = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val zone = ZoneId.of("Asia/Shanghai")

        val normalized = TodoDatePolicy.effectiveDeadline(legacy, zone, isDateOnly = true)

        assertEquals(date, Instant.ofEpochMilli(normalized).atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(23, 59, 59, 999_000_000), Instant.ofEpochMilli(normalized).atZone(zone).toLocalTime())
    }

    @Test
    fun `date only deadline becomes overdue at local day end and not before`() {
        val date = LocalDate.of(2026, 9, 20)
        val selected = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val zone = ZoneId.of("America/Los_Angeles")
        val dueAt = TodoDatePolicy.effectiveDeadline(
            TodoDatePolicy.dateOnlyDeadline(selected),
            zone,
            isDateOnly = true,
        )

        assertTrue(!TodoDeadlinePolicy.isOverdue(dueAt, dueAt - 1))
        assertTrue(TodoDeadlinePolicy.isOverdue(dueAt, dueAt))
    }

    @Test
    fun `editing a date only deadline changes only its calendar date`() {
        val original = TodoDatePolicy.dateOnlyDeadline(
            LocalDate.of(2026, 9, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val selected = LocalDate.of(2026, 9, 22).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val updated = TodoDatePolicy.updateDeadlineDate(
            original,
            selected,
            ZoneId.of("America/Los_Angeles"),
            existingIsDateOnly = true,
        )

        assertEquals(selected, updated)
        assertTrue(TodoDatePolicy.isDateOnlyValue(updated))
    }

    @Test
    fun `editing date preserves local time and lets zone rules resolve a DST gap`() {
        val zone = ZoneId.of("America/New_York")
        val original = LocalDate.of(2026, 3, 7).atTime(2, 30).atZone(zone).toInstant().toEpochMilli()
        val springForwardDate = LocalDate.of(2026, 3, 8)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val updated = Instant.ofEpochMilli(
            TodoDatePolicy.updateDeadlineDate(original, springForwardDate, zone),
        ).atZone(zone)

        assertEquals(LocalDate.of(2026, 3, 8), updated.toLocalDate())
        // 02:30 does not exist on this date; java.time advances it through the one-hour gap.
        assertEquals(LocalTime.of(3, 30), updated.toLocalTime())
    }

    @Test
    fun `editing a timed UTC midnight deadline keeps local clock time`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val original = LocalDate.of(2026, 9, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selected = LocalDate.of(2026, 9, 22).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val updated = Instant.ofEpochMilli(
            TodoDatePolicy.updateDeadlineDate(original, selected, zone, existingIsDateOnly = false),
        ).atZone(zone)

        assertEquals(LocalDate.of(2026, 9, 22), updated.toLocalDate())
        assertEquals(LocalTime.of(8, 0), updated.toLocalTime())
    }
}
