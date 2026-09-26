package com.xmu.course.data.transcript

import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.contracts.grades.TranscriptSnapshot

/**
 * 按声明顺序合并多个只读来源；每条课程保留自己的 source。
 *
 * 全部来源为空时返回 null（Unavailable）；来源不一致时快照级 source
 * 保持 null，由课程级 source 表达事实，不伪造统一来源。
 */
class CompositeTranscriptReader(
    private val readers: List<TranscriptReader>,
) : TranscriptReader {

    override suspend fun getTranscript(): TranscriptSnapshot? {
        val snapshots = readers.mapNotNull { it.getTranscript() }
        if (snapshots.isEmpty()) return null

        val first = snapshots.first()
        val distinctSources = snapshots.map { it.source }.distinct()

        return first.copy(
            courses = snapshots.flatMap { it.courses },
            source = if (distinctSources.size == 1) distinctSources.single() else null,
        )
    }
}
