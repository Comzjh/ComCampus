package com.xmu.course.data.academiccompletion

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

data class AcademicDiagnosticEnvironment(
    val appVersionName: String,
    val appVersionCode: Int,
    val androidApiLevel: Int,
)

/** Writes a single app-private ZIP containing only a fixed, aggregate-only diagnostic schema. */
class AcademicRefreshDiagnosticArchive(
    private val outputDirectory: File,
    private val environment: AcademicDiagnosticEnvironment,
    private val capturedAtUtc: () -> String = { Instant.now().toString() },
) {
    val latestArchive: File
        get() = File(outputDirectory, ARCHIVE_FILE_NAME)

    /** Replaces the prior report so this feature retains at most one archive. */
    fun write(diagnostics: AcademicRefreshDiagnostics): File {
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IllegalStateException("diagnostic directory unavailable")
        }

        val temporary = File(outputDirectory, "$ARCHIVE_FILE_NAME.tmp")
        try {
            FileOutputStream(temporary).use { fileOutput ->
                ZipOutputStream(fileOutput).use { zip ->
                    zip.putNextEntry(ZipEntry(REPORT_ENTRY_NAME))
                    zip.write(reportJson(diagnostics).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.finish()
                    fileOutput.fd.sync()
                }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    latestArchive.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    latestArchive.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            return latestArchive
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private fun reportJson(diagnostics: AcademicRefreshDiagnostics): String {
        val report = JSONObject()
            .put("schemaVersion", REPORT_SCHEMA_VERSION)
            .put("capturedAtUtc", capturedAtUtc())
            .put(
                "app",
                JSONObject()
                    .put("versionName", environment.appVersionName.take(MAX_VERSION_NAME_LENGTH))
                    .put("versionCode", environment.appVersionCode.coerceAtLeast(0)),
            )
            .put("androidApiLevel", environment.androidApiLevel.coerceAtLeast(0))
            .put(
                "failure",
                JSONObject()
                    .put("stage", diagnostics.stage.name)
                    .put("code", diagnostics.code.name),
            )

        diagnostics.reconciliation?.let { summary ->
            report.put(
                "sources",
                JSONObject()
                    .put("coursePoolRows", summary.coursePoolRowCount.coerceAtLeast(0))
                    .put("coursePoolDistinctCodes", summary.coursePoolDistinctCodeCount.coerceAtLeast(0))
                    .put("semesterCourseRows", summary.semesterCourseRowCount.coerceAtLeast(0))
                    .put("semesterCourseDistinctCodes", summary.semesterCourseDistinctCodeCount.coerceAtLeast(0))
                    .put("inPlanCourses", summary.inPlanCourseCount.coerceAtLeast(0))
                    .put("outOfPlanCourses", summary.outOfPlanCourseCount.coerceAtLeast(0))
                    .put("inPlanMissingCredits", summary.inPlanMissingCreditCount.coerceAtLeast(0))
                    .put("confirmedInPlanCredits", summary.confirmedInPlanCreditCount.coerceAtLeast(0)),
            )
            report.put(
                "reconciliation",
                JSONObject()
                    .put("inPlanCreditsSum", safeCredits(summary.inPlanCreditsSum))
                    .put("planLevelCredits", safeCredits(summary.planLevelCredits))
                    .put("matches", summary.reconciledWithPlanLevel),
            )
        }
        return report.toString(2)
    }

    private fun safeCredits(value: String): Any =
        CreditsDecimal.normalizeNonNegative(value)
            ?.takeIf { it.length <= MAX_CREDIT_LENGTH }
            ?: JSONObject.NULL

    private companion object {
        const val ARCHIVE_FILE_NAME = "academic-refresh-diagnostic.zip"
        const val REPORT_ENTRY_NAME = "diagnostic.json"
        const val REPORT_SCHEMA_VERSION = 1
        const val MAX_VERSION_NAME_LENGTH = 32
        const val MAX_CREDIT_LENGTH = 24
    }
}
