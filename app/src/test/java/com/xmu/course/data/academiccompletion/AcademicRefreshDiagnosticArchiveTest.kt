package com.xmu.course.data.academiccompletion

import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicRefreshDiagnosticArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesOnlyWhitelistedAggregateMetadataAndReplacesThePreviousZip() {
        val writer = AcademicRefreshDiagnosticArchive(
            outputDirectory = temporaryFolder.newFolder("private-diagnostics"),
            environment = AcademicDiagnosticEnvironment(
                appVersionName = "0.9.4",
                appVersionCode = 94,
                androidApiLevel = 35,
            ),
            capturedAtUtc = { "2026-09-29T10:00:00Z" },
        )
        val diagnostics = AcademicRefreshDiagnostics(
            stage = AcademicRefreshDiagnosticStage.SNAPSHOT_VALIDATION,
            code = AcademicRefreshDiagnosticCode.PLAN_CREDIT_TOTAL_MISMATCH,
            reconciliation = AcademicCreditReconciliationSummary(
                coursePoolRowCount = 12,
                coursePoolDistinctCodeCount = 12,
                semesterCourseRowCount = 4,
                semesterCourseDistinctCodeCount = 3,
                inPlanCourseCount = 2,
                outOfPlanCourseCount = 1,
                inPlanMissingCreditCount = 0,
                confirmedInPlanCreditCount = 2,
                inPlanCreditsSum = "5.25",
                planLevelCredits = "7.25",
                reconciledWithPlanLevel = false,
            ),
        )

        val first = writer.write(diagnostics)
        val firstReport = readOnlyReport(first)
        val parsed = JSONObject(firstReport)
        assertEquals("PLAN_CREDIT_TOTAL_MISMATCH", parsed.getJSONObject("failure").getString("code"))
        assertEquals("5.25", parsed.getJSONObject("reconciliation").getString("inPlanCreditsSum"))
        assertEquals("7.25", parsed.getJSONObject("reconciliation").getString("planLevelCredits"))
        assertEquals(3, parsed.getJSONObject("sources").getInt("semesterCourseDistinctCodes"))
        listOf("studentId", "courseCode", "courseName", "teacher", "grade", "cookie", "token", "rawResponse")
            .forEach { assertFalse("unexpected report field: $it", firstReport.contains(it)) }

        val second = writer.write(diagnostics.copy(code = AcademicRefreshDiagnosticCode.SOURCE_VALIDATION_REJECTED))
        assertEquals(first, second)
        assertEquals("SOURCE_VALIDATION_REJECTED", JSONObject(readOnlyReport(second))
            .getJSONObject("failure").getString("code"))
        assertEquals(listOf("academic-refresh-diagnostic.zip"),
            first.parentFile?.list()?.toList()?.sorted())
    }

    private fun readOnlyReport(file: File): String {
        ZipInputStream(file.inputStream()).use { zip ->
            assertEquals("diagnostic.json", zip.nextEntry?.name)
            val report = zip.readBytes().toString(Charsets.UTF_8)
            assertTrue(report.isNotBlank())
            assertEquals(null, zip.nextEntry)
            return report
        }
    }
}
