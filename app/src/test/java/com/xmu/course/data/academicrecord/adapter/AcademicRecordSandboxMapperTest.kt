package com.xmu.course.data.academicrecord.adapter

import com.xmu.course.contracts.academicrecord.AcademicRecord
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicRecordSandboxMapperTest {
    @Test
    fun mapsRecordToEditableDraftWithoutSource() {
        val draft = AcademicRecordSandboxMapper.map(
            AcademicRecord(
                name = "微积分II-2",
                creditsText = "5",
                source = AcademicRecordSource.JW_REPORT,
            ),
        )

        assertEquals("微积分II-2", draft.name)
        assertEquals("5", draft.credits)
        assertEquals("", draft.grade)
        assertEquals(
            setOf("name", "credits", "grade"),
            draft::class.java.declaredFields
                .filterNot { it.name.startsWith("$") }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun mapsBatchAndPreservesOriginalCreditText() {
        val drafts = AcademicRecordSandboxMapper.map(
            listOf(
                AcademicRecord("线性代数", "4.0", AcademicRecordSource.MANUAL_IMPORT),
                AcademicRecord("大学物理", "2 学时", AcademicRecordSource.JW_REPORT),
            ),
        )

        assertEquals(listOf("线性代数", "大学物理"), drafts.map { it.name })
        assertEquals(listOf("4.0", "2 学时"), drafts.map { it.credits })
        assertEquals(listOf("", ""), drafts.map { it.grade })
    }
}
