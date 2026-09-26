package com.xmu.course.ui.widget

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoDeadlineDisplayTest {

    private val nowMillis = Instant.parse("2026-09-14T02:00:00Z").toEpochMilli()

    private fun at(durationMillis: Long) = TodoDeadlineDisplay.resolve(nowMillis + durationMillis, nowMillis)

    @Test fun `100 hours displays five days`() = assertEquals("还有5D", at(100 * 60 * 60 * 1_000L).text)
    @Test fun `73 hours rounds up to four days`() = assertEquals("还有4D", at(73 * 60 * 60 * 1_000L).text)
    @Test fun `72 hours is three days and upcoming`() {
        val result = at(72 * 60 * 60 * 1_000L)
        assertEquals("还有3D", result.text)
        assertEquals(TodoUrgency.UPCOMING, result.urgency)
    }
    @Test fun `25 hours rounds up to two days`() = assertEquals("还有2D", at(25 * 60 * 60 * 1_000L).text)
    @Test fun `24 hours stays in hour display`() = assertEquals("还有24H", at(24 * 60 * 60 * 1_000L).text)
    @Test fun `six hours one minute rounds up to seven hours`() = assertEquals("还有7H", at(6 * 60 * 60 * 1_000L + 60_000L).text)
    @Test fun `six hours is critical boundary`() {
        val result = at(6 * 60 * 60 * 1_000L)
        assertEquals("还有6H", result.text)
        assertEquals(TodoUrgency.CRITICAL, result.urgency)
    }
    @Test fun `five hours one minute rounds up to six hours`() = assertEquals("还有6H", at(5 * 60 * 60 * 1_000L + 60_000L).text)
    @Test fun `fifty nine minutes one second rounds up`() = assertEquals("还有60m", at(59 * 60_000L + 1_000L).text)
    @Test fun `one minute stays one minute`() = assertEquals("还有1m", at(60_000L).text)
    @Test fun `one second stays visible as one minute`() = assertEquals("还有1m", at(1_000L).text)
    @Test fun `at deadline is overdue`() {
        val result = at(0L)
        assertEquals("已逾期1m", result.text)
        assertEquals(TodoUrgency.OVERDUE, result.urgency)
    }
    @Test fun `thirty minutes overdue`() = assertEquals("已逾期30m", at(-30 * 60_000L).text)
    @Test fun `three hours overdue`() = assertEquals("已逾期3H", at(-3 * 60 * 60 * 1_000L).text)
    @Test fun `forty nine hours overdue uses elapsed whole days`() = assertEquals("已逾期2D", at(-49 * 60 * 60 * 1_000L).text)
    @Test fun `no deadline has no prefix`() {
        val result = TodoDeadlineDisplay.resolve(null, nowMillis)
        assertNull(result.text)
        assertEquals(TodoUrgency.NORMAL, result.urgency)
    }

    @Test fun `more than seventy two hours is normal`() = assertEquals(TodoUrgency.NORMAL, at(73 * 60 * 60 * 1_000L).urgency)
    @Test fun `twenty five hours is upcoming`() = assertEquals(TodoUrgency.UPCOMING, at(25 * 60 * 60 * 1_000L).urgency)
    @Test fun `twenty four hours is urgent`() = assertEquals(TodoUrgency.URGENT, at(24 * 60 * 60 * 1_000L).urgency)
    @Test fun `twelve hours is urgent`() = assertEquals(TodoUrgency.URGENT, at(12 * 60 * 60 * 1_000L).urgency)
    @Test fun `one hour is critical`() = assertEquals(TodoUrgency.CRITICAL, at(60 * 60 * 1_000L).urgency)
    @Test fun `one minute is critical`() = assertEquals(TodoUrgency.CRITICAL, at(60_000L).urgency)
    @Test fun `overdue is always overdue urgency`() = assertEquals(TodoUrgency.OVERDUE, at(-1_000L).urgency)

    @Test fun `deadline one millisecond around boundary uses inclusive rule`() {
        assertEquals(TodoUrgency.OVERDUE, at(-1L).urgency)
        assertEquals(TodoUrgency.OVERDUE, at(0L).urgency)
        assertEquals(TodoUrgency.CRITICAL, at(1L).urgency)
    }
}
