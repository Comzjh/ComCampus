package com.xmu.course.data.import.provider

import com.xmu.course.data.import.CourseImportContract
import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult

/**
 * Transitional bridge from the source-agnostic provider boundary to the
 * existing Wisedu import contract.
 *
 * It deliberately contains no parsing, WebView, session, or persistence code;
 * the existing repository remains the owner of those behaviors.
 */
class CourseImportProviderAdapter(
    private val legacyImport: CourseImportContract,
) : CourseImportProvider {
    override suspend fun preview(input: CourseImportInput): ImportPreparation =
        legacyImport.prepareImport(input.rawHtml)

    override suspend fun commit(
        preparation: ImportPreparation,
        overwrite: Boolean,
    ): ImportResult = when (preparation) {
        is ImportPreparation.Ready -> legacyImport.commitImport(
            semester = preparation.semester,
            courses = preparation.courses,
            overwrite = overwrite,
        )
        is ImportPreparation.Conflict -> legacyImport.commitImport(
            semester = preparation.semester,
            courses = preparation.pendingCourses,
            overwrite = overwrite,
        )
    }
}
