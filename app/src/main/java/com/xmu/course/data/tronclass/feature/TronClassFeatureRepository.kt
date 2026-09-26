package com.xmu.course.data.tronclass.feature

import com.xmu.course.data.tronclass.model.TronCourseUiModel
import kotlinx.coroutines.flow.Flow

/** TronClass feature 面向 UI 的能力边界；integration protocol 在 adapter 内部转换。 */
interface TronClassFeatureRepository {
    fun observeCourses(): Flow<List<TronCourseUiModel>>

    suspend fun refreshSession(): TronClassAuthUiState

    suspend fun syncCourses(): TronClassCourseSyncUiResult

    suspend fun syncTodoSources(): TronClassTodoSyncUiResult

    suspend fun logout(): TronClassLogoutUiResult
}
