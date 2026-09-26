package com.xmu.course.data.widget

import android.content.Context
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Widget 刷新所需的数据变化边界。
 *
 * WidgetUpdater 只关心“是否需要刷新”，不直接了解 Room/DAO 的组织方式。
 */
interface WidgetDataSource {

    fun observeInvalidation(): Flow<Unit>

    companion object {
        fun from(context: Context): WidgetDataSource =
            RoomWidgetDataSource(context.applicationContext)
    }
}

private class RoomWidgetDataSource(
    context: Context,
) : WidgetDataSource {

    private val database by lazy { AppDatabase.getInstance(context) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeInvalidation(): Flow<Unit> {
        val currentCourses = TimetablePrefs.currentTimetableId.flatMapLatest { timetableId ->
            flow {
                val resolvedId = timetableId
                    ?: database.timetableDao().getAll().firstOrNull()?.id
                if (resolvedId == null) {
                    emit(Unit)
                } else {
                    emitAll(database.courseDao().observeByTimetableId(resolvedId).map { Unit })
                }
            }
        }
        return merge(
            currentCourses,
            database.skippedCourseDao().observeSkippedCourseIds().map { Unit },
            database.timetableDao().observeAllWithCount().map { Unit },
            database.todoDao().observeAll().map { Unit },
        )
    }
}
