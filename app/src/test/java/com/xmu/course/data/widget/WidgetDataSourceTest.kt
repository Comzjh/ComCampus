package com.xmu.course.data.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetDataSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        AppDatabase.closeAndResetForTest()
        context.deleteDatabase("xmu_course.db")
        TimetablePrefs.load(context)
    }

    @After
    fun tearDown() {
        AppDatabase.closeAndResetForTest()
        context.deleteDatabase("xmu_course.db")
    }

    @Test
    fun roomDataSourceEmitsForEmptyDatabaseFallback() = runBlocking {
        val invalidation = withTimeout(5_000L) {
            WidgetDataSource.from(context).observeInvalidation().first()
        }

        assertEquals(Unit, invalidation)
    }
}
