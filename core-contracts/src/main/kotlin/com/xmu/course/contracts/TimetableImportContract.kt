package com.xmu.course.contracts

import com.xmu.course.domain.Timetable

/** ImportCoordinator 所需的导入课表绑定能力。 */
interface TimetableImportContract {
    suspend fun ensureForSemester(
        semesterId: Long,
        name: String,
        startDate: String?,
    ): Timetable
}
