package com.xmu.course.data.import.adapter

import com.xmu.course.data.import.provider.CourseImportInput

class JwCourseImportAdapter {

    fun adapt(renderedHtml: String): CourseImportInput =
        CourseImportInput(rawHtml = renderedHtml)
}
