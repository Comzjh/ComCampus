package com.xmu.course.data.tronclass.matcher

import com.xmu.course.data.local.CourseEntity
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.domain.Course
import com.xmu.course.domain.normalizeCourseName

enum class MatchStrategy {
    ExactName,
    NormalizedName,
    FuzzyName,
}

data class MatchResult(
    val localCourseId: Long,
    val tronCourse: TronCourseEntity,
    val strategy: MatchStrategy,
)

/** 运行时课程关联器：只返回唯一、可解释的匹配，不写入任何数据库字段。 */
object CourseMatcher {
    fun match(localCourse: Course, tronCourses: List<TronCourseEntity>): MatchResult? =
        match(localCourse.id, localCourse.name, tronCourses)

    fun match(localCourse: CourseEntity, tronCourses: List<TronCourseEntity>): MatchResult? =
        match(localCourse.id, localCourse.name, tronCourses)

    private fun match(
        localCourseId: Long,
        localName: String,
        tronCourses: List<TronCourseEntity>,
    ): MatchResult? {
        val trimmedLocalName = localName.trim()
        if (trimmedLocalName.isEmpty()) return null

        unique(tronCourses.filter { it.name.trim() == trimmedLocalName })?.let {
            return MatchResult(localCourseId, it, MatchStrategy.ExactName)
        }

        val normalizedLocalName = normalizeCourseName(trimmedLocalName)
        if (normalizedLocalName.isEmpty()) return null
        unique(tronCourses.filter { normalizeCourseName(it.name) == normalizedLocalName })?.let {
            return MatchResult(localCourseId, it, MatchStrategy.NormalizedName)
        }

        if (normalizedLocalName.length < MIN_FUZZY_LENGTH) return null
        val fuzzyCandidates = tronCourses.filter { candidate ->
            val normalizedCandidate = normalizeCourseName(candidate.name)
            normalizedCandidate.length >= MIN_FUZZY_LENGTH &&
                (normalizedCandidate.contains(normalizedLocalName) || normalizedLocalName.contains(normalizedCandidate))
        }
        return unique(fuzzyCandidates)?.let {
            MatchResult(localCourseId, it, MatchStrategy.FuzzyName)
        }
    }

    private fun unique(courses: List<TronCourseEntity>): TronCourseEntity? =
        courses.distinctBy { it.tronCourseId }.singleOrNull()

    private const val MIN_FUZZY_LENGTH = 4
}
