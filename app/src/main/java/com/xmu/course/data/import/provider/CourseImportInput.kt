package com.xmu.course.data.import.provider

/**
 * Course-import input exposed at the provider boundary.
 *
 * This represents the import payload, not its origin. The current flows supply
 * rendered JW HTML or local HTML content. Session and WebView details stay
 * outside this contract so that feature code remains provider-agnostic.
 */
data class CourseImportInput(
    val rawHtml: String,
)
