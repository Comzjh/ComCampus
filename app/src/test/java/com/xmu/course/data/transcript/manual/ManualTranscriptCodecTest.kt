package com.xmu.course.data.transcript.manual

import org.junit.Assert.assertEquals
import org.junit.Test

/** 编解码往返：竖线、反斜杠、换行全部显式转义；坏行只跳过，不伪装成合法数据。 */
class ManualTranscriptCodecTest {

    @Test
    fun `round trip keeps every field exactly`() {
        val entries = listOf(
            ManualTranscriptEntry("数学|期中", "3.0", "85", "2025春"),
            ManualTranscriptEntry("物理\\实验", "2", "90", "2025秋"),
            ManualTranscriptEntry("化学\n笔记", "1.5", "77", "2026春"),
        )
        val encoded = ManualTranscriptCodec.encode(entries)
        assertEquals(entries, ManualTranscriptCodec.decode(encoded))
    }

    @Test
    fun `blank raw decodes to empty list`() {
        assertEquals(emptyList<ManualTranscriptEntry>(), ManualTranscriptCodec.decode(""))
        assertEquals(
            emptyList<ManualTranscriptEntry>(),
            ManualTranscriptCodec.decode("   \n   "),
        )
    }

    @Test
    fun `malformed lines are skipped without touching valid rows`() {
        val decoded = ManualTranscriptCodec.decode(
            "数学|3.0|85|2025春\n坏行|缺字段\n|3.0|85|2025春",
        )
        assertEquals(1, decoded.size)
        assertEquals("数学", decoded[0].courseName)
    }
}
