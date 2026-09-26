package com.xmu.course.data.presentation

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
class SharedPreferencesOnboardingPreferenceTest {

    @Test
    fun `wrapper preserves app onboarding preference`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        preferences.edit().remove("onboarding_done").commit()

        try {
            val onboardingPreference = SharedPreferencesOnboardingPreference(context)

            assertFalse(onboardingPreference.isOnboardingCompleted())
            onboardingPreference.setOnboardingCompleted()
            assertTrue(onboardingPreference.isOnboardingCompleted())
        } finally {
            preferences.edit().remove("onboarding_done").commit()
        }
    }
}
