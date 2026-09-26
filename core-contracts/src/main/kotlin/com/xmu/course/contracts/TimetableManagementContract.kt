package com.xmu.course.contracts

import com.xmu.course.domain.Timetable
import kotlinx.coroutines.flow.Flow

/** TimetableManagerViewModel 所需的最小课表管理能力。 */
interface TimetableManagementContract {
    fun observeTimetables(): Flow<List<TimetableSummary>>

    suspend fun createTimetable(name: String): Timetable

    suspend fun rename(id: Long, name: String)

    suspend fun deleteTimetable(id: Long)
}
