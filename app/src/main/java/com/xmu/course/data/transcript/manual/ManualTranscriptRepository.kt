package com.xmu.course.data.transcript.manual

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手动成绩录入的本地存储（SharedPreferences + StateFlow）。
 *
 * 不进 Room：与 TimetablePrefs 同一模式，避免 schema 迁移风险；
 * 数据只在用户显式录入/删除时变化，不触发任何网络或同步。
 */
class ManualTranscriptRepository(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
) {
    private val _entries = MutableStateFlow(
        ManualTranscriptCodec.decode(prefs.getString(KEY_ENTRIES, "").orEmpty())
    )
    val entries: StateFlow<List<ManualTranscriptEntry>> = _entries.asStateFlow()

    fun add(entry: ManualTranscriptEntry) {
        _entries.value = _entries.value + entry
        persist()
    }

    fun remove(entry: ManualTranscriptEntry) {
        _entries.value = _entries.value.filterNot { it == entry }
        persist()
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_ENTRIES, ManualTranscriptCodec.encode(_entries.value))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "manual_transcript_prefs"
        const val KEY_ENTRIES = "manual_transcript_entries"
    }
}
