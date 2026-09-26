package com.xmu.course.data.tronclass.feature

import com.xmu.course.data.tronclass.model.TronCourseUiModel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TronClassFeatureRepositoryContractTest {

    @Test
    fun contractCanBeImplementedWithoutIntegrationProtocolTypes() {
        val fake = object : TronClassFeatureRepository {
            override fun observeCourses(): Flow<List<TronCourseUiModel>> = flowOf(emptyList())

            override suspend fun refreshSession() = TronClassAuthUiState.UNAUTHENTICATED

            override suspend fun syncCourses() = TronClassCourseSyncUiResult.Success(0)

            override suspend fun syncTodoSources() = TronClassTodoSyncUiResult.Success(0)

            override suspend fun logout() = TronClassLogoutUiResult.Success
        }

        runBlocking {
            assertEquals(emptyList<TronCourseUiModel>(), fake.observeCourses().first())
        }
    }

    @Test
    fun contractSourceDoesNotDependOnIntegrationProtocolTypes() {
        val source = Files.readAllLines(featureSource("TronClassFeatureRepository.kt"))
            .joinToString("\n")
        val forbidden = listOf(
            "TronCourseEntity",
            "TronResult",
            "TronSyncState",
            "TronClassError",
            "TronClassRepositoryContract",
            "android.",
            "androidx.",
        )

        assertTrue(
            "TronClass feature contract must not expose integration protocol types",
            forbidden.none(source::contains),
        )
    }

    private fun featureSource(fileName: String): Path {
        val candidates = listOf(
            Paths.get("src", "main", "java", "com", "xmu", "course", "data", "tronclass", "feature", fileName),
            Paths.get("app", "src", "main", "java", "com", "xmu", "course", "data", "tronclass", "feature", fileName),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Feature source not found: $fileName")
    }
}
