package com.xmu.course.data.transcript.manual

/**
 * 手动录入条目与持久化字符串之间的纯 Kotlin 编解码。
 *
 * 行内字段以 | 分隔；字段值中的反斜杠、竖线、换行做显式转义。
 * 任何无法解释的行被跳过，不伪装成合法数据。
 */
object ManualTranscriptCodec {

    fun encode(entries: List<ManualTranscriptEntry>): String =
        entries.joinToString("\n") { entry ->
            listOf(entry.courseName, entry.creditsText, entry.scoreText, entry.term)
                .joinToString("|") { escape(it) }
        }

    fun decode(raw: String): List<ManualTranscriptEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                runCatching {
                    val fields = splitFields(line)
                    require(fields.size == 4) { "field count mismatch" }
                    ManualTranscriptEntry(
                        courseName = fields[0],
                        creditsText = fields[1],
                        scoreText = fields[2],
                        term = fields[3],
                    )
                }.getOrNull()
            }
            .toList()
    }

    private fun escape(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '|' -> append("\\|")
                '\n' -> append("\\n")
                else -> append(ch)
            }
        }
    }

    private fun splitFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in line) {
            when {
                escaped -> {
                    when (ch) {
                        'n' -> current.append('\n')
                        else -> current.append(ch)
                    }
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == '|' -> { fields.add(current.toString()); current.setLength(0) }
                else -> current.append(ch)
            }
        }
        if (escaped) current.append('\\')
        fields.add(current.toString())
        return fields
    }
}
