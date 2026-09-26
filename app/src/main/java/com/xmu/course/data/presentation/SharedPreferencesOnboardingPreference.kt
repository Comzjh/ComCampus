package com.xmu.course.data.presentation

import android.content.Context
import com.xmu.course.contracts.presentation.OnboardingPreference

/** SharedPreferences-backed implementation for the app onboarding capability. */
class SharedPreferencesOnboardingPreference(
    context: Context,
) : OnboardingPreference {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun isOnboardingCompleted(): Boolean =
        preferences.getBoolean(KEY_ONBOARDING_DONE, false)

    override fun setOnboardingCompleted() {
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    private companion object {
        const val PREFERENCES = "app_prefs"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
