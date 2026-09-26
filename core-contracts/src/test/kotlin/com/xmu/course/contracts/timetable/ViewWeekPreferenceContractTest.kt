package com.xmu.course.contracts.timetable

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewWeekPreferenceContractTest {

    @Test
    fun `contract supports per timetable read and write`() {
        val values = mutableMapOf<Long, Int>()
        val preference = object : ViewWeekPreference {
            override fun getViewWeek(timetableId: Long): Int? = values[timetableId]

            override fun setViewWeek(timetableId: Long, week: Int) {
                values[timetableId] = week
            }
        }

        assertEquals(null, preference.getViewWeek(1L))
        preference.setViewWeek(1L, 8)
        assertEquals(8, preference.getViewWeek(1L))
        assertEquals(null, preference.getViewWeek(2L))
    }
}
