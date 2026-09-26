package com.xmu.course.data.academicrecord

import com.xmu.course.contracts.academicimport.ConfirmedAcademicCourse
import com.xmu.course.contracts.academicrecord.AcademicRecordSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicRecordMapperTest {
    @Test
    fun mapsOnlyConfirmedCourseFieldsAndExplicitSource() {
        val result = AcademicRecordMapper.fromConfirmed(
            course = ConfirmedAcademicCourse(name = "微积分II-2", creditText = "5"),
            source = AcademicRecordSource.JW_REPORT,
        )

        assertEquals("微积分II-2", result.name)
        assertEquals("5", result.creditsText)
        assertEquals(AcademicRecordSource.JW_REPORT, result.source)
        assertEquals(setOf("name", "creditsText", "source"), result::class.java.declaredFields.map { it.name }.toSet())
    }

    @Test
    fun mapsAConfirmedBatchWithoutChangingCreditText() {
        val result = AcademicRecordMapper.fromConfirmed(
            courses = listOf(
                ConfirmedAcademicCourse(name = "线性代数", creditText = "4.0"),
                ConfirmedAcademicCourse(name = "大学物理", creditText = "2 小时"),
            ),
            source = AcademicRecordSource.MANUAL_IMPORT,
        )

        assertEquals(listOf("线性代数", "大学物理"), result.map { it.name })
        assertEquals(listOf("4.0", "2 小时"), result.map { it.creditsText })
        assertEquals(listOf(AcademicRecordSource.MANUAL_IMPORT, AcademicRecordSource.MANUAL_IMPORT), result.map { it.source })
    }
}
