package com.xmu.course.data.import

import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester

/**
 * Application-level import preview and user decision model.
 *
 * The UI uses Ready/Conflict to decide whether confirmation is required, and
 * the current provider reuses the same model at the application boundary.
 * Keep provider-specific private state out of this type.
 */
sealed class ImportPreparation {
    data class Ready(
        val semester: Semester,
        val courses: List<Course>,
    ) : ImportPreparation()

    data class Conflict(
        val semester: Semester,
        val existingCount: Int,
        val newCount: Int,
        val pendingCourses: List<Course>,
    ) : ImportPreparation()
}

/** Application-level result of committing an import. */
sealed class ImportResult {
    data class Success(val semesterId: Long, val count: Int) : ImportResult()
    data object Cancelled : ImportResult()
}
