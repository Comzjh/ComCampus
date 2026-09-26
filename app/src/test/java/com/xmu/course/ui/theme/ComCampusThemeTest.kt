package com.xmu.course.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComCampusThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeDefaultsToBrandBlueInLightMode() {
        var primary: Color? = null
        composeRule.setContent {
            XmuCourseTheme(darkTheme = false, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertEquals(0xFF1A5CAB.toInt(), primary?.toArgb())
    }

    @Test
    fun themeUsesLightBlueInDarkMode() {
        var primary: Color? = null
        composeRule.setContent {
            XmuCourseTheme(darkTheme = true, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertEquals(0xFF9ECAFF.toInt(), primary?.toArgb())
    }

    @Test
    fun followSystemColorPreferenceDefaultsOffAndToggles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ThemePreferences.ensureLoaded(context)
        assertEquals(false, ThemePreferences.followSystemColor.value)
        ThemePreferences.setFollowSystemColor(context, true)
        assertEquals(true, ThemePreferences.followSystemColor.value)
        ThemePreferences.setFollowSystemColor(context, false)
        assertEquals(false, ThemePreferences.followSystemColor.value)
    }

    @Test
    fun wrongFollowSystemColorTypeFallsBackToBrandDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
        val loadedField = ThemePreferences::class.java.getDeclaredField("loaded").apply {
            isAccessible = true
        }
        loadedField.setBoolean(ThemePreferences, false)
        preferences.edit().putString("follow_system_color", "corrupted").commit()

        try {
            ThemePreferences.ensureLoaded(context)
            assertEquals(false, ThemePreferences.followSystemColor.value)
        } finally {
            preferences.edit().remove("follow_system_color").commit()
            ThemePreferences.setFollowSystemColor(context, false)
            loadedField.setBoolean(ThemePreferences, true)
        }
    }
}
