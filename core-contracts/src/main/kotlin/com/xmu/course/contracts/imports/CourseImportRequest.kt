package com.xmu.course.contracts.imports

/** Feature 发起一次课程导入的意图，不表达 Provider transport。 */
data class CourseImportRequest(
    val source: CourseImportSource,
)

/** 课程导入能力的来源分类，不携带登录或实现细节。 */
enum class CourseImportSource {
    JW,
}
