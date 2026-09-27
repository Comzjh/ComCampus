package com.xmu.course.ui.widget

import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetThemeTest {
    @Test
    fun everyWidgetPaletteChangesBetweenSystemLightAndDarkModes() {
        WidgetBackgroundStyle.values().forEach { style ->
            assertNotEquals(
                "${style.name} must provide distinct day and night colors",
                WidgetTheme.gradientColors(style, isDark = false),
                WidgetTheme.gradientColors(style, isDark = true),
            )
        }
    }
}
