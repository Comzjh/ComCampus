package com.xmu.course.data.transcript

import com.xmu.course.domain.Course
import com.xmu.course.domain.Semester

/** 将现有课表领域数据转换为 Transcript 读取模型，不推断成绩或学分。 */
object TranscriptMapper {
    fun fromCourses(
        semester: Semester,
        courses: List<Course>,
    ): TranscriptSnapshot = TranscriptSnapshot(
        semester = SemesterRef(
            name = semester.name,
            code = semester.code,
        ),
        courses = courses.map { course ->
            CourseTranscriptItem(
                courseName = course.name,
                teacher = course.teacher,
                location = course.location,
            )
        },
    )
}
