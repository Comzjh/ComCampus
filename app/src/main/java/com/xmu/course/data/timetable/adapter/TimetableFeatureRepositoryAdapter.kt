package com.xmu.course.data.timetable.adapter

import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.tron.TronCourseObservationContract
import com.xmu.course.data.tronclass.matcher.CourseMatchMapper
import com.xmu.course.data.tronclass.matcher.CourseMatcher
import com.xmu.course.contracts.CourseManagementContract
import com.xmu.course.contracts.TimetableManagementContract
import com.xmu.course.contracts.TimetableObservationContract
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 将既有 data capabilities 组合为 Timetable feature capability；不替换旧仓库路径。
 *
 * 配置方法通过函数参数接入，避免为一次迁移而改造 TimetableRepository 的公共类型。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableFeatureRepositoryAdapter(
    private val timetableManagement: TimetableManagementContract,
    private val timetableObservation: TimetableObservationContract,
    private val courseManagement: CourseManagementContract,
    private val observeConfig: (Long) -> Flow<TimetableConfig>,
    private val saveConfig: suspend (TimetableConfig) -> Unit,
    private val tronCourseObservation: TronCourseObservationContract,
    private val currentTimetableId: StateFlow<Long?> = TimetablePrefs.currentTimetableId,
    private val onCurrentTimetableResolved: (Long) -> Unit = {},
) : TimetableFeatureRepository {

    override fun observeCurrentTimetableState(): Flow<TimetableFeatureState> =
        combine(
            timetableManagement.observeTimetables(),
            currentTimetableId,
        ) { summaries, preferredId -> summaries to preferredId }
            .flatMapLatest { (summaries, preferredId) ->
                val chosen = summaries.firstOrNull { it.timetable.id == preferredId }?.timetable
                    ?: summaries.firstOrNull()?.timetable
                if (chosen == null) {
                    flowOf(TimetableFeatureState())
                } else {
                    if (chosen.id != preferredId) onCurrentTimetableResolved(chosen.id)
                    combine(
                        timetableObservation.observeTimetable(chosen.id),
                        courseManagement.observeCoursesByTimetable(chosen.id),
                        observeConfig(chosen.id),
                        courseManagement.observeSkippedCourseIds(),
                        tronCourseObservation.observeCourses(),
                    ) { observed, courses, config, skipped, tronCourses ->
                        val timetable = observed ?: chosen
                        val links = courses.mapNotNull { course ->
                            CourseMatcher.match(course, tronCourses)?.let { match ->
                                course.id to TimetableMatchMapper.toFeatureModel(
                                    CourseMatchMapper.toTimetableUiModel(match),
                                )
                            }
                        }.toMap()
                        TimetableFeatureState(
                            timetable = timetable,
                            courses = courses,
                            config = config,
                            skippedCourseIds = skipped,
                            timetableLinks = links,
                        )
                    }
                }
            }

    override suspend fun addCourse(course: Course) {
        val timetable = observeCurrentTimetableState().first().timetable ?: return
        courseManagement.addCourse(timetable.semesterId, course)
    }

    override suspend fun updateCourseColor(courseId: Long, color: String) {
        courseManagement.updateCourseColor(courseId, color)
    }

    override suspend fun updateCourseNote(courseId: Long, note: String) {
        courseManagement.updateCourseNote(courseId, note)
    }

    override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) {
        val current = courseManagement.observeSkippedCourseIds().first().toMutableSet()
        if (skipped) current += courseId else current -= courseId
        courseManagement.saveSkippedCourses(current)
    }

    override suspend fun updateConfig(config: TimetableConfig) {
        saveConfig(config)
    }
}
