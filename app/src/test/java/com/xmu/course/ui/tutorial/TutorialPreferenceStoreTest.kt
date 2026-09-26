package com.xmu.course.ui.tutorial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialPreferenceStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun startsIncompleteAndPersistsCompletion() {
        val store = SharedPreferencesTutorialPreferenceStore(context)
        val seenKey = "tutorial_seen_registry_test_v1"
        assertFalse(store.hasCompleted(seenKey))
        store.markCompleted(seenKey)
        assertTrue(store.hasCompleted(seenKey))
    }

    @Test
    fun versionedKeysAreIndependent() {
        val store = SharedPreferencesTutorialPreferenceStore(context)
        store.markCompleted("tutorial_seen_registry_test_v1")
        assertTrue(store.hasCompleted("tutorial_seen_registry_test_v1"))
        assertFalse(store.hasCompleted("tutorial_seen_registry_test_v2"))
    }
}
