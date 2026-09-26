package com.xmu.course.data.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.contracts.presentation.StartupDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesStartupDestinationPreferenceTest {

    @Test
    fun `startup destination round-trips and unknown value reads as null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("startup_destination", Context.MODE_PRIVATE)
        preferences.edit().remove("destination").commit()

        try {
            val preference = SharedPreferencesStartupDestinationPreference(context)

            assertNull(preference.destination())
            preference.setDestination(StartupDestination.TIMETABLE)
            assertEquals(StartupDestination.TIMETABLE, preference.destination())

            preferences.edit().putString("destination", "MYSTERY").commit()
            assertNull(preference.destination())

            preference.setDestination(StartupDestination.HOME)
            assertEquals(StartupDestination.HOME, preference.destination())
        } finally {
            preferences.edit().remove("destination").commit()
        }
    }

    @Test
    fun `wrong stored type falls back safely and can be replaced`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("startup_destination", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("destination", true).commit()

        try {
            val preference = SharedPreferencesStartupDestinationPreference(context)

            assertNull(preference.destination())
            assertEquals(true, preferences.getBoolean("destination", false))

            preference.setDestination(StartupDestination.TIMETABLE)
            assertEquals(StartupDestination.TIMETABLE, preference.destination())
        } finally {
            preferences.edit().remove("destination").commit()
        }
    }
}
