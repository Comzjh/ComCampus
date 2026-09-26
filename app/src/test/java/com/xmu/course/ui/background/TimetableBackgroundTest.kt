package com.xmu.course.ui.background

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableBackgroundTest {

    @Test
    fun `default white timetable background follows dark theme`() {
        val darkBackground = Color(0xFF0F1319)

        assertEquals(
            darkBackground,
            themedSolidColor("#FFFFFFFF", darkTheme = true, darkBackground = darkBackground),
        )
        assertEquals(
            Color.White,
            themedSolidColor("#FFFFFFFF", darkTheme = false, darkBackground = darkBackground),
        )
    }

    @Test
    fun `custom solid colors remain unchanged in dark theme`() {
        val darkBackground = Color(0xFF0F1319)
        val custom = Color(0xFF202124)

        assertEquals(
            custom,
            themedSolidColor("#FF202124", darkTheme = true, darkBackground = darkBackground),
        )
    }
}
