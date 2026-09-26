package com.xmu.course

import com.xmu.course.domain.Course
import com.xmu.course.domain.cleanImportedCourseName
import com.xmu.course.domain.mergeImportedCourses
import com.xmu.course.domain.normalizeCourseName
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseNameNormalizerTest {
    @Test
    fun `清洗导入课程名会去除括号阿拉伯数字和空格`() {
        assertEquals("Python程序设计", cleanImportedCourseName(" Python 程序设计（11） "))
    }

    @Test
    fun `比较标准化会去除标点`() {
        assertEquals("Python程序设计", normalizeCourseName("Python程序设计（11）"))
    }

    @Test
    fun `末尾班号会被清洗`() {
        assertEquals("高等数学", cleanImportedCourseName("高等数学第1班"))
    }

    private fun course(
        name: String,
        day: Int = 1,
        start: Int = 1,
        duration: Int = 2,
        teacher: String = "张三",
        location: String = "海韵104",
        weeks: Set<Int> = setOf(1),
    ) = Course(
        name = name,
        teacher = teacher,
        location = location,
        dayOfWeek = day,
        startSection = start,
        duration = duration,
        weeks = weeks,
    )

    @Test
    fun `相同课程身份合并并合并周次`() {
        val merged = mergeImportedCourses(
            listOf(
                course("Python程序设计（11）", weeks = setOf(1, 2)),
                course("Python程序设计", weeks = setOf(3, 4)),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals("Python程序设计", merged.single().name)
        assertEquals(setOf(1, 2, 3, 4), merged.single().weeks)
    }

    @Test
    fun `同名但时间不同不合并`() {
        val merged = mergeImportedCourses(
            listOf(course("Python程序设计", day = 1), course("Python程序设计（11）", day = 3)),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `同名但教师或地点不同不合并`() {
        val merged = mergeImportedCourses(
            listOf(
                course("Python程序设计", teacher = "张三"),
                course("Python程序设计", teacher = "李四"),
                course("Python程序设计", location = "嘉庚101"),
            ),
        )
        assertEquals(3, merged.size)
    }
}
