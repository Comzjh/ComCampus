package com.xmu.course.ui.manager

import com.xmu.course.contracts.TimetableManagementContract
import com.xmu.course.contracts.TimetableSummary
import com.xmu.course.domain.Timetable
import kotlinx.coroutines.flow.MutableStateFlow

/** Observable management source; unexpected create/rename/delete operations fail the regression. */
internal class TimetableStartDateTestFixture(
    initial: List<TimetableSummary> = listOf(customStartDateSummary(1L), customStartDateSummary(2L, "2026-09-07")),
) : TimetableManagementContract {
    val summaries = MutableStateFlow(initial)
    val savedDates = mutableListOf<Pair<Long, String>>()

    override fun observeTimetables() = summaries
    override suspend fun createTimetable(name: String): Timetable = error("Unexpected create")
    override suspend fun rename(id: Long, name: String) { error("Unexpected rename") }
    override suspend fun deleteTimetable(id: Long) { error("Unexpected delete") }

    suspend fun saveStartDate(id: Long, date: String) {
        savedDates += id to date
        summaries.value = summaries.value.map { summary ->
            if (summary.timetable.id == id) summary.copy(timetable = summary.timetable.copy(startDate = date))
            else summary
        }
    }
}

internal fun customStartDateSummary(id: Long, startDate: String? = null) = TimetableSummary(
    timetable = Timetable(
        id = id,
        name = "合成自建课表 $id",
        semesterId = 100L + id,
        startDate = startDate,
        totalWeeks = 20,
        currentWeek = 8,
        createdTime = 1_000L + id,
        color = "#123456",
    ),
    courseCount = 4,
    isCustom = true,
)
