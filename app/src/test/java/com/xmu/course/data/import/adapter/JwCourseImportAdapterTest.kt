package com.xmu.course.data.import.adapter

import com.xmu.course.data.import.provider.CourseImportInput
import org.junit.Assert.assertEquals
import org.junit.Test

class JwCourseImportAdapterTest {

    @Test
    fun `adapt preserves rendered JW HTML exactly`() {
        val renderedHtml = "<html><body><p>Test HTML content.</p></body></html>"

        val actual = JwCourseImportAdapter().adapt(renderedHtml)

        assertEquals(CourseImportInput(rawHtml = renderedHtml), actual)
    }

    @Test
    fun `adapt preserves blank input`() {
        val actual = JwCourseImportAdapter().adapt("")

        assertEquals(CourseImportInput(rawHtml = ""), actual)
    }
}
