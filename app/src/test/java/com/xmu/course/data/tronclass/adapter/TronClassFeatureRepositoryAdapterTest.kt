package com.xmu.course.data.tronclass.adapter

import com.xmu.course.data.tronclass.feature.TronClassAuthUiState
import com.xmu.course.data.tronclass.feature.TronClassCourseSyncUiResult
import com.xmu.course.data.tronclass.feature.TronClassFeatureRepository
import com.xmu.course.data.tronclass.feature.TronClassLogoutUiResult
import com.xmu.course.data.tronclass.feature.TronClassTodoSyncUiResult
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronClassUiError
import com.xmu.course.data.tronclass.model.TronClassUiErrorCategory
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.tronclass.model.TronSyncState
import com.xmu.course.data.tronclass.repository.TronClassRepositoryContract
import com.xmu.course.data.tronclass.repository.TronTodoSyncSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TronClassFeatureRepositoryAdapterTest {

    @Test
    fun mapsCoursesAndSessionStateWithoutExposingEntities() = runTest {
        val entity = TronCourseEntity(
            tronCourseId = 42L,
            name = "高等数学",
            semester = "2026-1",
            instructor = "老师",
            updatedTime = 100L,
        )
        val delegate = FakeRepository().apply {
            courses.value = listOf(entity)
            authState = true
        }
        val repository: TronClassFeatureRepository = TronClassFeatureRepositoryAdapter(delegate)

        assertEquals("高等数学", repository.observeCourses().first().single().name)
        assertEquals(42L, repository.observeCourses().first().single().id)
        assertEquals(TronClassAuthUiState.AUTHENTICATED, repository.refreshSession())
    }

    @Test
    fun mapsCourseSyncResults() = runTest {
        val delegate = FakeRepository()
        val repository = TronClassFeatureRepositoryAdapter(delegate)

        delegate.courseSyncResult = TronResult.Success(
            listOf(
                TronCourseEntity(
                    tronCourseId = 42L,
                    name = "高等数学",
                    semester = "2026-1",
                    instructor = "老师",
                    updatedTime = 100L,
                ),
            ),
        )
        assertEquals(TronClassCourseSyncUiResult.Success(1, 100L), repository.syncCourses())

        delegate.courseSyncResult = TronResult.Error(TronClassError.Unauthorized)
        assertEquals(TronClassCourseSyncUiResult.SessionExpired, repository.syncCourses())

        delegate.courseSyncResult = TronResult.Error(TronClassError.NetworkError)
        assertEquals(
            TronClassCourseSyncUiResult.Failed(
                TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
            ),
            repository.syncCourses(),
        )
    }

    @Test
    fun mapsTodoSyncAndLogoutResults() = runTest {
        val delegate = FakeRepository()
        val repository = TronClassFeatureRepositoryAdapter(delegate)

        delegate.todoSyncResult = TronResult.Success(TronTodoSyncSummary(2, 1))
        assertEquals(TronClassTodoSyncUiResult.Success(3), repository.syncTodoSources())

        delegate.todoSyncResult = TronResult.Error(TronClassError.Unauthorized)
        assertEquals(TronClassTodoSyncUiResult.SessionExpired, repository.syncTodoSources())

        delegate.todoSyncResult = TronResult.Error(TronClassError.NetworkError)
        assertEquals(
            TronClassTodoSyncUiResult.Failed(
                TronClassUiError(TronClassUiErrorCategory.NetworkFailure),
            ),
            repository.syncTodoSources(),
        )

        delegate.logoutResult = TronResult.Success(Unit)
        assertEquals(TronClassLogoutUiResult.Success, repository.logout())

        delegate.logoutResult = TronResult.Error(TronClassError.StorageError)
        assertEquals(
            TronClassLogoutUiResult.Failed(
                TronClassUiError(TronClassUiErrorCategory.StorageFailure),
            ),
            repository.logout(),
        )
    }

    private class FakeRepository : TronClassRepositoryContract {
        val courses = MutableStateFlow<List<TronCourseEntity>>(emptyList())
        var authState = false
        var courseSyncResult: TronResult<List<TronCourseEntity>> = TronResult.Success(emptyList())
        var todoSyncResult: TronResult<TronTodoSyncSummary> = TronResult.Success(TronTodoSyncSummary(0, 0))
        var logoutResult: TronResult<Unit> = TronResult.Success(Unit)

        override suspend fun syncCourses() = courseSyncResult

        override suspend fun syncCoursesState() = TronSyncState.Success(courses.value.size)

        override fun observeCourses(): Flow<List<TronCourseEntity>> = courses

        override suspend fun clearCourses() = TronResult.Success(Unit)

        override fun hasSession() = authState

        override suspend fun logout() = logoutResult

        override suspend fun syncTodoSources() = todoSyncResult

        override suspend fun syncAssignments() = TronResult.Success(0)
    }
}
