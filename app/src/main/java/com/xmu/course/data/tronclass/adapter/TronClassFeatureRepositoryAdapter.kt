package com.xmu.course.data.tronclass.adapter

import com.xmu.course.data.tronclass.feature.TronClassAuthUiState
import com.xmu.course.data.tronclass.feature.TronClassCourseSyncUiResult
import com.xmu.course.data.tronclass.feature.TronClassFeatureRepository
import com.xmu.course.data.tronclass.feature.TronClassLogoutUiResult
import com.xmu.course.data.tronclass.feature.TronClassTodoSyncUiResult
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronCourseUiModel
import com.xmu.course.data.tronclass.model.TronResult
import com.xmu.course.data.tronclass.model.toUiModel
import com.xmu.course.data.tronclass.model.toUiError
import com.xmu.course.data.tronclass.repository.TronClassRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 将现有 TronClass repository 协议适配为 feature capability，不改变旧同步路径。 */
class TronClassFeatureRepositoryAdapter(
    private val delegate: TronClassRepositoryContract,
) : TronClassFeatureRepository {
    override fun observeCourses(): Flow<List<TronCourseUiModel>> =
        delegate.observeCourses().map { courses -> courses.map { it.toUiModel() } }

    override suspend fun refreshSession(): TronClassAuthUiState =
        if (delegate.hasSession()) TronClassAuthUiState.AUTHENTICATED
        else TronClassAuthUiState.UNAUTHENTICATED

    override suspend fun syncCourses(): TronClassCourseSyncUiResult =
        when (val result = delegate.syncCourses()) {
            is TronResult.Success -> TronClassCourseSyncUiResult.Success(
                count = result.value.size,
                lastSyncTime = result.value.maxOfOrNull { it.updatedTime },
            )
            is TronResult.Error -> if (result.error == TronClassError.Unauthorized) {
                TronClassCourseSyncUiResult.SessionExpired
            } else {
                TronClassCourseSyncUiResult.Failed(result.error.toUiError())
            }
        }

    override suspend fun syncTodoSources(): TronClassTodoSyncUiResult =
        when (val result = delegate.syncTodoSources()) {
            is TronResult.Success -> TronClassTodoSyncUiResult.Success(result.value.totalCount)
            is TronResult.Error -> if (result.error.requiresAuthentication()) {
                TronClassTodoSyncUiResult.SessionExpired
            } else {
                TronClassTodoSyncUiResult.Failed(result.error.toUiError())
            }
        }

    override suspend fun logout(): TronClassLogoutUiResult =
        when (val result = delegate.logout()) {
            is TronResult.Success -> TronClassLogoutUiResult.Success
            is TronResult.Error -> TronClassLogoutUiResult.Failed(result.error.toUiError())
        }
}

private fun TronClassError.requiresAuthentication(): Boolean =
    this == TronClassError.Unauthorized ||
        this == TronClassError.SessionMissing ||
        this == TronClassError.AuthRequired
