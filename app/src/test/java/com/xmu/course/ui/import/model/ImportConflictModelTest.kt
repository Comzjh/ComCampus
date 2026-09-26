package com.xmu.course.ui.import.model

import com.xmu.course.domain.Semester
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportConflictModelTest {
    @Test
    fun keepsOnlyFieldsRequiredByConflictPresentation() {
        val model = ImportConflictModel(
            semester = Semester(name = "2026 秋季", code = "20261"),
            existingCount = 4,
            newCount = 6,
        )

        assertEquals("2026 秋季", model.semester.name)
        assertEquals(4, model.existingCount)
        assertEquals(6, model.newCount)
    }
}
