package com.xmu.course.data.transcript.manual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.grades.TranscriptSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 手动录入聚合读取：无数据返回 null；每条课程保留用户原文与 MANUAL 来源。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualTranscriptReaderTest {

    private fun repo(name: String): ManualTranscriptRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ManualTranscriptRepository(
            context,
            context.getSharedPreferences(name, Context.MODE_PRIVATE),
        )
    }

    @Test
    fun `空仓库返回 null 表示无可用快照`() = runTest {
        val reader = ManualTranscriptReader(repo("manual_reader_empty"))
        assertNull(reader.getTranscript())
    }

    @Test
    fun `条目聚合为快照且逐字段保留原文`() = runTest {
        val repository = repo("manual_reader_two")
        repository.add(ManualTranscriptEntry("数学", "3.0", "85", "2025春"))
        repository.add(ManualTranscriptEntry("体育", "1", "优秀", "2025秋"))

        val snapshot = ManualTranscriptReader(repository).getTranscript()!!

        assertEquals("手动录入", snapshot.semester.name)
        assertNull(snapshot.semester.code)
        assertEquals(TranscriptSource.MANUAL, snapshot.source)
        assertEquals(2, snapshot.courses.size)

        val math = snapshot.courses[0]
        assertEquals("数学", math.courseName)
        assertEquals("85", math.score)
        assertEquals(3.0, math.credits!!, 1e-9)
        assertEquals("3.0", math.creditsText)
        assertNull(math.gradeText)
        assertEquals("2025春", math.term)
        assertEquals(TranscriptSource.MANUAL, math.source)

        val pe = snapshot.courses[1]
        assertEquals("优秀", pe.score)
        assertEquals("2025秋", pe.term)
    }

    @Test
    fun `term 字段原样保留且不推断`() = runTest {
        val repository = repo("manual_reader_terms")
        repository.add(ManualTranscriptEntry("高数", "4", "90", "2025秋"))
        val snapshot = ManualTranscriptReader(repository).getTranscript()!!
        assertEquals(listOf("2025秋"), snapshot.courses.map { it.term })
    }
}
