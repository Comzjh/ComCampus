package com.xmu.course.ui.components

import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.xmu.course.ui.theme.action

/** Keep the exact cyan selection fill without using pale cyan for calendar text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
    navigationContentColor = MaterialTheme.colorScheme.action,
    currentYearContentColor = MaterialTheme.colorScheme.action,
    todayContentColor = MaterialTheme.colorScheme.action,
    todayDateBorderColor = MaterialTheme.colorScheme.action,
    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
)
