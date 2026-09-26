package com.xmu.course.contracts.grades

/** GPA Sandbox 可接受的课程草稿；不携带 PDF、Excel、Room 或 Provider 实现细节。 */
data class GpaSandboxCourseDraft(
    val courseName: String,
    val credit: Double,
) {
    init {
        require(courseName.isNotBlank()) { "courseName must not be blank" }
        require(credit.isFinite() && credit > 0.0) { "credit must be a positive finite number" }
    }
}

/** 一次“当前学期课表 + 外部学分数据”匹配的只读结果。 */
data class GpaSandboxPrefillResult(
    val semesterName: String?,
    val courses: List<GpaSandboxCourseDraft>,
    val duplicateTimetableCourseCount: Int,
    val unmatchedTimetableCourseNames: List<String>,
    val sourceAvailable: Boolean,
)

/** Grades 只依赖这个能力，不知道学分来自 PDF、Excel 还是未来其他 Provider。 */
interface GpaSandboxCourseSource {
    suspend fun loadCurrentSemesterCourses(): GpaSandboxPrefillResult
}
