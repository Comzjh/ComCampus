package com.xmu.course.contracts.academicrecord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AcademicRecordContractTest {
    @Test
    fun recordKeepsOnlyConfirmedAcademicFactFields() {
        val record = AcademicRecord(
            name = "微积分II-2",
            creditsText = "3.0",
            source = AcademicRecordSource.JW_REPORT,
        )

        assertEquals("微积分II-2", record.name)
        assertEquals("3.0", record.creditsText)
        assertEquals(AcademicRecordSource.JW_REPORT, record.source)
        assertEquals(
            setOf("name", "creditsText", "source"),
            record::class.java.declaredFields.map { it.name }.toSet(),
        )
    }

    @Test
    fun recordRejectsMissingConfirmedFields() {
        assertFailsWith<IllegalArgumentException> {
            AcademicRecord(name = "", creditsText = "3.0", source = AcademicRecordSource.JW_REPORT)
        }
        assertFailsWith<IllegalArgumentException> {
            AcademicRecord(name = "课程", creditsText = "", source = AcademicRecordSource.MANUAL_IMPORT)
        }
    }

    @Test
    fun sourceIsFiniteAndDoesNotEncodeTransportMetadata() {
        assertEquals(
            setOf(AcademicRecordSource.JW_REPORT, AcademicRecordSource.MANUAL_IMPORT),
            AcademicRecordSource.entries.toSet(),
        )

        val sourceRoot = java.nio.file.Paths.get(
            "src", "main", "kotlin", "com", "xmu", "course", "contracts", "academicrecord",
        )
        val source = java.nio.file.Files.walk(sourceRoot).use { paths ->
            paths.filter { java.nio.file.Files.isRegularFile(it) }
                .map { java.nio.file.Files.readString(it) }
                .toList()
                .joinToString("\n")
        }

        listOf("android.", "Uri", "InputStream", "Pdf", "Room", "Cookie", "Token", "GradeEntry").forEach {
            marker -> assertTrue(marker !in source, "Academic Record contract must not expose $marker")
        }
    }
}
