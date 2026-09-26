package com.xmu.course.data.timetable.feature

import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.model.TimetableFeatureState
import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.domain.TimetableConfig
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

class TimetableFeatureRepositoryContractTest {

    @Test
    fun contractCanBeImplementedWithFeatureModelsOnly() {
        val state = TimetableFeatureState(
            timetableLinks = mapOf(
                1L to TimetableMatchModel("高等数学", "教师", "2026 春"),
            ),
        )
        val fake = object : TimetableFeatureRepository {
            override fun observeCurrentTimetableState(): Flow<TimetableFeatureState> = flowOf(state)

            override suspend fun addCourse(course: Course) = Unit

            override suspend fun updateCourseColor(courseId: Long, color: String) = Unit

            override suspend fun updateCourseNote(courseId: Long, note: String) = Unit

            override suspend fun setCourseSkipped(courseId: Long, skipped: Boolean) = Unit

            override suspend fun updateConfig(config: TimetableConfig) = Unit
        }

        runBlocking {
            assertEquals(state, fake.observeCurrentTimetableState().first())
        }
    }

    @Test
    fun featureSourcesDoNotDependOnStorageOrIntegrationProtocolTypes() {
        val sources = listOf(
            contractSource("TimetableFeatureState.kt"),
            contractSource("TimetableFeatureRepository.kt"),
        ).joinToString("\n") { Files.readAllLines(it).joinToString("\n") }
        val forbidden = listOf(
            "TimetableEntity",
            "CourseEntity",
            "TronCourseEntity",
            "TronResult",
            "TronSyncState",
            "MatchResult",
            "MatchStrategy",
            "CourseMatcher",
            "TimetableDao",
            "SharedPreferences",
            "android.",
            "androidx.",
        )

        assertTrue(
            "Timetable feature contract must not expose storage or integration protocol types",
            forbidden.none(sources::contains),
        )
    }

    @Test
    fun domainCourseRemainsTheFeatureWriteModel() {
        val course = Course(
            name = "测试课程",
            dayOfWeek = 1,
            startSection = 1,
            duration = 2,
            weeks = setOf(1),
            source = CourseSource.MANUAL,
        )
        assertEquals(CourseSource.MANUAL, course.source)
    }

    private fun contractSource(fileName: String): Path {
        val candidates = listOf(
            Paths.get(
                "core-contracts",
                "src",
                "main",
                "kotlin",
                "com",
                "xmu",
                "course",
                "contracts",
                "timetable",
                "model",
                fileName,
            ),
            Paths.get(
                "core-contracts",
                "src",
                "main",
                "kotlin",
                "com",
                "xmu",
                "course",
                "contracts",
                "timetable",
                fileName,
            ),
            Paths.get(
                "..",
                "core-contracts",
                "src",
                "main",
                "kotlin",
                "com",
                "xmu",
                "course",
                "contracts",
                "timetable",
                "model",
                fileName,
            ),
            Paths.get(
                "..",
                "core-contracts",
                "src",
                "main",
                "kotlin",
                "com",
                "xmu",
                "course",
                "contracts",
                "timetable",
                fileName,
            ),
            Paths.get("src", "main", "java", "com", "xmu", "course", "data", "timetable", "feature", fileName),
            Paths.get("app", "src", "main", "java", "com", "xmu", "course", "data", "timetable", "feature", fileName),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Timetable contract source not found: $fileName")
    }
}
