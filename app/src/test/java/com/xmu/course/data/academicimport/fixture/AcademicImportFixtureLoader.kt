package com.xmu.course.data.academicimport.fixture

import com.xmu.course.data.academicimport.ExtractedAcademicDocument
import com.xmu.course.data.academicimport.ExtractedAcademicPage
import com.xmu.course.data.academicimport.ExtractedTextRun

/** 只供测试使用的脱敏 fixture loader；不读取用户 PDF，也不携带身份字段。 */
object AcademicImportFixtureLoader {
    fun coordinateDocument(): ExtractedAcademicDocument {
        val pages = mutableListOf<MutableList<ExtractedTextRun>>()
        var currentPage = -1
        resourceLines("academicimport/coordinate_sample_page.txt").forEach { line ->
            val parts = line.split('|', limit = 6)
            when (parts[0]) {
                "page" -> {
                    currentPage = parts[1].toInt() - 1
                    while (pages.size <= currentPage) pages.add(mutableListOf())
                }

                "run" -> {
                    check(currentPage >= 0) { "fixture run must follow a page" }
                    pages[currentPage] += ExtractedTextRun(
                        text = parts[5],
                        x = parts[1].toFloat(),
                        y = parts[2].toFloat(),
                        width = parts[3].toFloat(),
                        height = parts[4].toFloat(),
                    )
                }

                else -> error("unsupported fixture record: ${parts[0]}")
            }
        }
        return ExtractedAcademicDocument(
            pages = pages.mapIndexed { index, runs ->
                ExtractedAcademicPage(pageNumber = index + 1, textRuns = runs)
            },
        )
    }

    fun textSample(name: String): String = resourceLines("academicimport/text_samples.txt")
        .map { it.split('|', limit = 2) }
        .firstOrNull { it[0] == name }
        ?.get(1)
        ?: error("missing text fixture: $name")

    private fun resourceLines(path: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "missing test resource: $path"
        }.bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .toList()
        }
}
