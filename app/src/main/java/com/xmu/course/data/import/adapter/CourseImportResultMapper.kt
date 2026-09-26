package com.xmu.course.data.import.adapter

import com.xmu.course.contracts.imports.CourseImportResult
import com.xmu.course.data.import.ImportResult

/** Converts the legacy data outcome to the provider-neutral core outcome. */
object CourseImportResultMapper {
    fun map(result: ImportResult): CourseImportResult = when (result) {
        is ImportResult.Success -> CourseImportResult.Success(
            importedCount = result.count,
        )
        ImportResult.Cancelled -> CourseImportResult.NoChanges
    }
}
