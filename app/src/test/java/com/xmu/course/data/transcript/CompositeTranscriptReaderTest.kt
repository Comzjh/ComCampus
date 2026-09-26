package com.xmu.course.data.transcript

import com.xmu.course.contracts.grades.TranscriptCourse
import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.contracts.grades.TranscriptSemester
import com.xmu.course.contracts.grades.TranscriptSnapshot
import com.xmu.course.contracts.grades.TranscriptSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 多来源合并：全空为 null；同来源保留快照级 source；混来源只保留课程级事实。 */
class CompositeTranscriptReaderTest {

    private fun course(name: String, source: TranscriptSource?) =
        TranscriptCourse(name, null, null, source = source)

    private fun snapshot(
        courses: List<TranscriptCourse>,
        source: TranscriptSource?,
    ): TranscriptSnapshot = TranscriptSnapshot(
        semester = TranscriptSemester("测试学期", null),
        courses = courses,
        source = source,
    )

    private class FixedReader(private val snapshot: TranscriptSnapshot?) : TranscriptReader {
        override suspend fun getTranscript(): TranscriptSnapshot? = snapshot
    }

    @Test
    fun `全部来源为空时返回 null`() = runTest {
        val composite = CompositeTranscriptReader(
            listOf(FixedReader(null), FixedReader(null)),
        )
        assertNull(composite.getTranscript())
    }

    @Test
    fun `单一来源时快照级 source 保留`() = runTest {
        val manual = snapshot(listOf(course("数学", TranscriptSource.MANUAL)), TranscriptSource.MANUAL)
        val composite = CompositeTranscriptReader(
            listOf(FixedReader(null), FixedReader(manual)),
        )
        val merged = composite.getTranscript()!!
        assertEquals(TranscriptSource.MANUAL, merged.source)
        assertEquals(1, merged.courses.size)
    }

    @Test
    fun `混合来源时快照级 source 为 null 但课程级来源保留`() = runTest {
        val composite = CompositeTranscriptReader(
            listOf(
                FixedReader(snapshot(listOf(course("数学", TranscriptSource.ACADEMIC_IMPORT)), TranscriptSource.ACADEMIC_IMPORT)),
                FixedReader(snapshot(listOf(course("体育", TranscriptSource.MANUAL)), TranscriptSource.MANUAL)),
            ),
        )
        val merged = composite.getTranscript()!!

        assertNull(merged.source)
        assertEquals("测试学期", merged.semester.name)
        assertEquals(listOf("数学", "体育"), merged.courses.map { it.courseName })
        assertEquals(
            listOf(TranscriptSource.ACADEMIC_IMPORT, TranscriptSource.MANUAL),
            merged.courses.map { it.source },
        )
    }
}
