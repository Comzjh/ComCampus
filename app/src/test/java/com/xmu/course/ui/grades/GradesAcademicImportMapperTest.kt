package com.xmu.course.ui.grades

import com.xmu.course.data.academicrecord.adapter.AcademicRecordSandboxDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesAcademicImportMapperTest {
    @Test
    fun mapsDraftToEditableSandboxItemWithNewIdAndEmptyGrade() {
        val item = GradesAcademicImportMapper().map(
            AcademicRecordSandboxDraft(
                name = "微积分II-2",
                credits = "5",
                grade = "A",
            ),
        )

        assertEquals("微积分II-2", item.name)
        assertEquals("5", item.credits)
        assertEquals("", item.grade)
        assertTrue(item.id > 0L)
    }

    @Test
    fun mapsBatchWithUniqueSessionIdsAndDoesNotLeakDraftMetadata() {
        val mapper = GradesAcademicImportMapper()

        val items = mapper.map(
            listOf(
                AcademicRecordSandboxDraft("课程一", "1", grade = "B"),
                AcademicRecordSandboxDraft("课程二", "2", grade = "C"),
            ),
        )

        assertEquals(2, items.size)
        assertNotEquals(items[0].id, items[1].id)
        assertEquals(listOf("课程一", "课程二"), items.map { it.name })
        assertEquals(listOf("1", "2"), items.map { it.credits })
        assertTrue(items.all { it.grade.isEmpty() })
    }
}
