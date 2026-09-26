package com.xmu.course.ui.import.model

import com.xmu.course.domain.Semester

/** Import conflict fields required by the UI, without provider or storage details. */
data class ImportConflictModel(
    val semester: Semester,
    val existingCount: Int,
    val newCount: Int,
)
