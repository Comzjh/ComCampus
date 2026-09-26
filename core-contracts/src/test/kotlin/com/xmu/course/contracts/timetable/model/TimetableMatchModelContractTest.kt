package com.xmu.course.contracts.timetable.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TimetableMatchModelContractTest {
    @Test
    fun `model exposes only timetable display fields`() {
        val model = TimetableMatchModel(
            name = "高等数学",
            instructor = "教师",
            semester = "2026 春",
        )

        assertEquals("高等数学", model.name)
        assertEquals("教师", model.instructor)
        assertEquals("2026 春", model.semester)
        assertEquals(
            setOf("name", "instructor", "semester"),
            model::class.java.declaredFields.map { it.name }.toSet(),
        )
    }
}
