package com.xmu.course.domain

/**
 * 课程名称的展示清洗与比较标准化规则。
 * 导入时只移除明显的班号标记，不删除课程名中的普通标点或数字。
 */
fun cleanImportedCourseName(name: String): String = name
    .trim()
    .replace(Regex("\\s+"), "")
    .replace(Regex("[（(]\\s*\\d+\\s*[）)]"), "")
    .replace(Regex("(?:第)?\\d+\\s*班$"), "")
    .trim()

/** 用于匹配的名称：在导入清洗基础上去除中英文标点。 */
fun normalizeCourseName(name: String): String = cleanImportedCourseName(name)
    .replace(Regex("[\\p{Punct}，。！？、：；“”‘’《》【】〈〉]"), "")

/**
 * 合并导入解析出的重复课程。
 * 不同星期、节次或时长的同名课程不能合并，否则 Course 的单一时间字段会丢失课表信息。
 */
fun mergeImportedCourses(courses: List<Course>): List<Course> {
    data class MergeKey(
        val normalizedName: String,
        val teacher: String,
        val location: String,
        val dayOfWeek: Int,
        val startSection: Int,
        val duration: Int,
    )

    val merged = LinkedHashMap<MergeKey, Course>()
    courses.forEach { course ->
        val cleanedName = cleanImportedCourseName(course.name)
        val key = MergeKey(
            normalizedName = normalizeCourseName(cleanedName),
            teacher = course.teacher.trim(),
            location = course.location.trim(),
            dayOfWeek = course.dayOfWeek,
            startSection = course.startSection,
            duration = course.duration,
        )
        val previous = merged[key]
        merged[key] = if (previous == null) {
            course.copy(name = cleanedName)
        } else {
            previous.copy(weeks = previous.weeks + course.weeks)
        }
    }
    return merged.values.toList()
}
