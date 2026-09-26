package com.xmu.course.data.academicimport.parser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.PdfBoxTextExtractor
import com.xmu.course.data.academicimport.fixture.AcademicImportFixtureLoader
import com.xmu.course.data.academicimport.parser.AcademicParseConfidence.HIGH
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 本地样例验收：只读取 Desktop 中的用户样例，不复制、不保存、不输出身份字段。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicReportSampleVerificationTest {
    @Test
    fun sanitizedCoordinateFixtureRemainsDeterministicAndIdentityFree() {
        val result = AcademicReportParser().parse(AcademicImportFixtureLoader.coordinateDocument())
        val records = when (result) {
            is AcademicImportParseResult.Parsed -> result.records
            is AcademicImportParseResult.NeedsReview -> result.records
            is AcademicImportParseResult.Failed -> error("sanitized fixture should contain text")
        }

        assertTrue(records.any { it.name == "高等数学" && it.creditText == "4.0" })
        assertTrue(records.any { it.name == "社会实践" && it.creditText == "0.5" })
        assertTrue(records.any { it.name == "跨页课程" && it.pageNumber == 2 })
        assertTrue(records.any { it.name == "待确认课程" && it.creditText == null })
        assertFalse(records.any { it.name?.contains("学号") == true || it.name?.contains("姓名") == true })
    }

    @Test
    fun sampleReportProducesCompletedCourseCandidates() {
        val sample = File(System.getProperty("user.home"), "Desktop/学业完成查询.pdf")
        assumeTrue(sample.isFile)

        val document = sample.inputStream().use {
            PdfBoxTextExtractor(ApplicationProvider.getApplicationContext<Application>()).extract(it)
        }
        val result = AcademicReportParser().parse(document)
        val records = when (result) {
            is AcademicImportParseResult.Parsed -> result.records
            is AcademicImportParseResult.NeedsReview -> result.records
            is AcademicImportParseResult.Failed -> error("sample report should contain text")
        }

        assertEquals(53, document.pageCount)
        assertTrue(records.size >= 4)
        assertTrue(records.any { it.name == "微积分II-2" && it.creditText == "5.0" })
        assertTrue(records.any { it.name == "微积分II-1" && it.creditText == "3.0" })

        val completed = records.filter { it.kind == AcademicRecordKind.COMPLETED }
        assertTrue(completed.isNotEmpty())
        assertTrue(completed.all { !it.name.isNullOrBlank() && !it.creditText.isNullOrBlank() })
        val confirmableCompleted = completed.filter { item ->
            item.confidence == HIGH &&
                item.name?.endsWith("（") != true &&
                item.name?.endsWith("(") != true
        }
        val completedKeys = confirmableCompleted.map { "${it.name}|${it.creditText}".lowercase(Locale.ROOT) }
        assertEquals("duplicate confirmable candidates: ${completedKeys.groupingBy { it }.eachCount().filterValues { it > 1 }}", confirmableCompleted.size, completedKeys.toSet().size)
        val nonCourseHeadings = listOf("要求学分", "已完成学分", "本学期已选学分", "课组是否通过")
        assertFalse(completed.any { item -> nonCourseHeadings.any { heading -> item.name?.contains(heading) == true } })
    }
}
