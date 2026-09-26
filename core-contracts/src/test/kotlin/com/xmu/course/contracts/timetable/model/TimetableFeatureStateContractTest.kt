package com.xmu.course.contracts.timetable.model

import com.xmu.course.domain.TimetableConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimetableFeatureStateContractTest {

    @Test
    fun `state exposes only stable feature data`() {
        val state = TimetableFeatureState()

        assertTrue(state.courses.isEmpty())
        assertEquals(TimetableConfig(timetableId = 0L), state.config)
        assertTrue(state.skippedCourseIds.isEmpty())
        assertTrue(state.timetableLinks.isEmpty())
    }
}
