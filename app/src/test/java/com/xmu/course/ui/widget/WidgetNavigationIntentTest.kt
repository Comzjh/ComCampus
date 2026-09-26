package com.xmu.course.ui.widget

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetNavigationIntentTest {
    @Test
    fun `todo widget intent opens todo route in existing activity`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = buildTodoWidgetIntent(context)

        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertTrue(intent.getBooleanExtra(EXTRA_WIDGET_OPEN_TODO, false))
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}
