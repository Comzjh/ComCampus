package com.xmu.course.ui.import

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportUiStateTest {
    @Test
    fun startDatePickerVisibilityIsAnExplicitPresentationSignal() {
        assertFalse(ImportUiState().shouldShowStartDatePicker)
        assertTrue(ImportUiState(shouldShowStartDatePicker = true).shouldShowStartDatePicker)
    }
}
