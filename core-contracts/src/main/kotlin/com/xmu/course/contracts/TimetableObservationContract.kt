package com.xmu.course.contracts

import com.xmu.course.domain.Timetable
import kotlinx.coroutines.flow.Flow

/** CourseManagerViewModel 所需的单课表观察能力。 */
interface TimetableObservationContract {
    fun observeTimetable(id: Long): Flow<Timetable?>
}
