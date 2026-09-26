package com.xmu.course.data.import.provider

import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult

/**
 * Provider boundary for course import.
 *
 * This is the feature-facing import boundary. The current preview/result
 * models are application-level import decisions; the deprecated repository
 * contract stays behind [CourseImportProviderAdapter].
 */
interface CourseImportProvider {
    suspend fun preview(input: CourseImportInput): ImportPreparation

    suspend fun commit(
        preparation: ImportPreparation,
        overwrite: Boolean,
    ): ImportResult
}
