package com.xmu.course.domain.todo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** DatePicker stores calendar dates at UTC midnight; Todo stores absolute epoch instants. */
object TodoDatePolicy {
    /** Date-only deadlines use a UTC-midnight calendar-date value, as earlier DatePicker entries did. */
    fun isDateOnlyValue(deadlineMillis: Long): Boolean =
        Math.floorMod(deadlineMillis, MILLIS_PER_DAY) == 0L

    /** Convert a stored date-only value into the current zone's final instant for comparisons/display. */
    fun effectiveDeadline(
        deadlineMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        isDateOnly: Boolean = false,
    ): Long = if (isDateOnly && isDateOnlyValue(deadlineMillis)) {
        selectedDate(deadlineMillis)
            .plusDays(1)
            .atStartOfDay(zoneId)
            .minusNanos(1)
            .toInstant()
            .toEpochMilli()
    } else {
        deadlineMillis
    }

    fun pickerDateMillis(
        deadlineMillis: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        isDateOnly: Boolean = false,
    ): Long? =
        deadlineMillis?.let { millis ->
            if (isDateOnly && isDateOnlyValue(millis)) {
                selectedDate(millis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else {
                val localDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        }

    /** Store the selected calendar date at UTC midnight; it remains a date if the device zone changes. */
    fun dateOnlyDeadline(selectedUtcDateMillis: Long): Long =
        selectedDate(selectedUtcDateMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Change the calendar date while retaining an existing deadline's local time of day. */
    fun updateDeadlineDate(
        existingDeadlineMillis: Long?,
        selectedUtcDateMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        existingIsDateOnly: Boolean = false,
    ): Long {
        val date = selectedDate(selectedUtcDateMillis)
        if (existingDeadlineMillis == null || (existingIsDateOnly && isDateOnlyValue(existingDeadlineMillis))) {
            return dateOnlyDeadline(selectedUtcDateMillis)
        }
        val existingTime = existingDeadlineMillis?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime()
        }
        return existingTime?.let { date.atTime(it).atZone(zoneId).toInstant().toEpochMilli() }
            ?: dateOnlyDeadline(selectedUtcDateMillis)
    }

    fun isEndOfLocalDay(deadlineMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val date = Instant.ofEpochMilli(deadlineMillis).atZone(zoneId).toLocalDate()
        return date.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant().toEpochMilli() == deadlineMillis
    }

    private fun selectedDate(selectedUtcDateMillis: Long): LocalDate =
        Instant.ofEpochMilli(selectedUtcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()

    private const val MILLIS_PER_DAY = 86_400_000L
}
