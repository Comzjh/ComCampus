package com.xmu.course.data.presentation

import android.content.Context
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.contracts.presentation.StartupDestinationPreference

/** SharedPreferences-backed startup destination preference; unknown/absent value reads as null. */
class SharedPreferencesStartupDestinationPreference(
    context: Context,
) : StartupDestinationPreference {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun destination(): StartupDestination? {
        val saved = try {
            preferences.getString(KEY_DESTINATION, null)
        } catch (_: ClassCastException) {
            return null
        }
        return saved?.let { value ->
            runCatching { StartupDestination.valueOf(value) }.getOrNull()
        }
    }

    override fun setDestination(destination: StartupDestination) {
        preferences.edit().putString(KEY_DESTINATION, destination.name).apply()
    }

    private companion object {
        const val PREFERENCES = "startup_destination"
        const val KEY_DESTINATION = "destination"
    }
}
