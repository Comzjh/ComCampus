package com.xmu.course.data.import

import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester

/**
 * Legacy repository import seam kept during the provider migration.
 *
 * New feature-facing code must depend on `CourseImportProvider` instead.
 * Retire this interface only after the repository capability is migrated
 * behind an internal gateway and the adapter no longer needs this seam.
 */
@Deprecated(
    message = "Use CourseImportProvider; retire after the repository seam is migrated.",
    level = DeprecationLevel.WARNING,
)
interface CourseImportContract {
    suspend fun prepareImport(html: String): ImportPreparation

    suspend fun commitImport(
        semester: Semester,
        courses: List<Course>,
        overwrite: Boolean,
    ): ImportResult
}
