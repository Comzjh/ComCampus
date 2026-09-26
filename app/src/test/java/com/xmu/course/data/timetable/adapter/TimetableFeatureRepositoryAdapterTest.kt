package com.xmu.course.data.timetable.adapter

import com.xmu.course.contracts.CourseManagementContract
import com.xmu.course.contracts.TimetableManagementContract
import com.xmu.course.contracts.TimetableObservationContract
import com.xmu.course.contracts.TimetableSummary
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.data.tron.TronCourseObservationContract
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.Timetable
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableFeatureRepositoryAdapterTest {

    @Test
    fun `adapter aggregates current timetable courses config skipped ids and links`() = runTest {
        val timetable = timetable()
        val course = course(timetable.semesterId)
        val courseManagement = FakeCourseManagement(course)
        val adapter = adapter(timetable, courseManagement)

        assertEquals(
            TimetableFeatureState(
                timetable = timetable,
                courses = listOf(course),
                config = TimetableConfig(timetable.id),
                skippedCourseIds = setOf(course.id),
                timetableLinks = mapOf(
                    course.id to TimetableMatchModel("高等数学", "教师", "2026 春"),
                ),
            ),
            adapter.observeCurrentTimetableState().first(),
        )
    }

    @Test
    fun `adapter delegates feature writes without changing old contracts`() = runTest {
        val timetable = timetable()
        val courseManagement = FakeCourseManagement(course(timetable.semesterId))
        var savedConfig: TimetableConfig? = null
        val adapter = adapter(timetable, courseManagement) { savedConfig = it }

        adapter.updateCourseColor(8L, "#123456")
        adapter.updateCourseNote(8L, "备注")
        adapter.setCourseSkipped(8L, false)
        adapter.updateConfig(TimetableConfig(timetable.id, textSize = 14))
        adapter.addCourse(course(timetable.semesterId).copy(id = 9L))

        assertEquals(8L to "#123456", courseManagement.colorUpdate)
        assertEquals(8L to "备注", courseManagement.noteUpdate)
        assertEquals(emptySet<Long>(), courseManagement.savedSkipped)
        assertEquals(TimetableConfig(timetable.id, textSize = 14), savedConfig)
        assertEquals(timetable.semesterId to 9L, courseManagement.addedCourse)
    }

    @Test
    fun `stale current selection self heals when timetable data returns`() = runTest {
        val summaries = MutableStateFlow(emptyList<TimetableSummary>())
        val currentId = MutableStateFlow<Long?>(999L)
        var resolvedId: Long? = null
        val timetable = timetable()
        val adapter = TimetableFeatureRepositoryAdapter(
            timetableManagement = object : TimetableManagementContract {
                override fun observeTimetables(): Flow<List<TimetableSummary>> = summaries
                override suspend fun createTimetable(name: String) = error("unused")
                override suspend fun rename(id: Long, name: String) = error("unused")
                override suspend fun deleteTimetable(id: Long) = error("unused")
            },
            timetableObservation = FakeTimetableObservation(timetable),
            courseManagement = FakeCourseManagement(course(timetable.semesterId)),
            observeConfig = { flowOf(TimetableConfig(timetable.id)) },
            saveConfig = {},
            tronCourseObservation = FakeTronCourseObservation(),
            currentTimetableId = currentId,
            onCurrentTimetableResolved = { id ->
                resolvedId = id
                currentId.value = id
            },
        )
        val observedStates = mutableListOf<TimetableFeatureState>()
        val observer = launch { adapter.observeCurrentTimetableState().collect(observedStates::add) }

        runCurrent()
        assertNull(observedStates.last().timetable)
        assertEquals(999L, currentId.value)
        assertNull(resolvedId)

        summaries.value = listOf(TimetableSummary(timetable, courseCount = 0, isCustom = false))
        runCurrent()

        assertEquals(timetable, observedStates.last().timetable)
        assertEquals(timetable.id, resolvedId)
        assertEquals(timetable.id, currentId.value)
        observer.cancel()
    }

    private fun adapter(
        timetable: Timetable,
        courseManagement: FakeCourseManagement,
        onSaveConfig: (TimetableConfig) -> Unit = {},
    ): TimetableFeatureRepository = TimetableFeatureRepositoryAdapter(
        timetableManagement = FakeTimetableManagement(timetable),
        timetableObservation = FakeTimetableObservation(timetable),
        courseManagement = courseManagement,
        observeConfig = { flowOf(TimetableConfig(timetable.id)) },
        saveConfig = { onSaveConfig(it) },
        tronCourseObservation = FakeTronCourseObservation(),
        currentTimetableId = MutableStateFlow(null),
    )

    private fun timetable() = Timetable(
        id = 3L,
        name = "2026 春",
        semesterId = 4L,
    )

    private fun course(semesterId: Long) = Course(
        id = 8L,
        semesterId = semesterId,
        name = "高等 数学",
        teacher = "教师",
        dayOfWeek = 1,
        startSection = 1,
        duration = 2,
        weeks = setOf(1),
        source = CourseSource.IMPORT,
    )

    private class FakeTimetableManagement(
        private val timetable: Timetable,
    ) : TimetableManagementContract {
        override fun observeTimetables(): Flow<List<TimetableSummary>> = flowOf(
            listOf(TimetableSummary(timetable, courseCount = 1, isCustom = false)),
        )

        override suspend fun createTimetable(name: String) = error("unused")
        override suspend fun rename(id: Long, name: String) = error("unused")
        override suspend fun deleteTimetable(id: Long) = error("unused")
    }

    private class FakeTimetableObservation(
        private val timetable: Timetable,
    ) : TimetableObservationContract {
        override fun observeTimetable(id: Long): Flow<Timetable?> = flowOf(timetable)
    }

    private class FakeTronCourseObservation : TronCourseObservationContract {
        override fun observeCourses(): Flow<List<TronCourseEntity>> = flowOf(
            listOf(
                TronCourseEntity(
                    tronCourseId = 12L,
                    name = "高等数学",
                    semester = "2026 春",
                    instructor = "教师",
                    updatedTime = 10L,
                ),
            ),
        )
    }

    private class FakeCourseManagement(
        course: Course,
    ) : CourseManagementContract {
        private val courses = listOf(course)
        private val skipped = MutableStateFlow(setOf(course.id))
        var colorUpdate: Pair<Long, String>? = null
        var noteUpdate: Pair<Long, String>? = null
        var savedSkipped: Set<Long>? = null
        var addedCourse: Pair<Long, Long>? = null

        override fun observeCoursesByTimetable(timetableId: Long): Flow<List<Course>> = flowOf(courses)
        override fun observeSkippedCourseIds(): Flow<Set<Long>> = skipped
        override suspend fun addCourse(semesterId: Long, course: Course) {
            addedCourse = semesterId to course.id
        }
        override suspend fun updateCourseName(courseId: Long, name: String) = Unit
        override suspend fun updateCourseTeacher(courseId: Long, teacher: String) = Unit
        override suspend fun updateCourseLocation(courseId: Long, location: String) = Unit
        override suspend fun updateCourseNote(courseId: Long, note: String) {
            noteUpdate = courseId to note
        }
        override suspend fun updateCourseColor(courseId: Long, color: String) {
            colorUpdate = courseId to color
        }
        override suspend fun saveSkippedCourses(courseIds: Collection<Long>) {
            savedSkipped = courseIds.toSet()
        }
        override suspend fun deleteSemesterCourses(semesterId: Long) = Unit
        override suspend fun deleteCourse(courseId: Long) = Unit
    }
}
