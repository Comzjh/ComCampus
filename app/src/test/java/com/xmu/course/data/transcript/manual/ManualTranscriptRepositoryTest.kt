package com.xmu.course.data.transcript.manual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualTranscriptRepositoryTest {

    @Test
    fun addRemoveAndRecreatePreservesTheExpectedEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("manual_transcript_lifecycle_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        try {
            val first = ManualTranscriptEntry("合成课程甲", "3.0", "85", "2025春")
            val second = ManualTranscriptEntry("合成课程乙", "1.5", "等级制", "2025秋")
            val writer = ManualTranscriptRepository(context, preferences)

            writer.add(first)
            writer.add(second)
            val reopened = ManualTranscriptRepository(context, preferences)
            assertEquals(listOf(first, second), reopened.entries.value)

            reopened.remove(first)
            val reopenedAfterRemoval = ManualTranscriptRepository(context, preferences)
            assertEquals(listOf(second), reopenedAfterRemoval.entries.value)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
