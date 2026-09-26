package com.xmu.course.data.import.adapter

import com.xmu.course.contracts.imports.CourseImportResult
import com.xmu.course.data.import.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseImportResultMapperTest {
    @Test
    fun successDropsStorageIdAndKeepsImportedCount() {
        val result = CourseImportResultMapper.map(
            ImportResult.Success(semesterId = 42L, count = 12),
        )

        assertEquals(CourseImportResult.Success(importedCount = 12), result)
    }

    @Test
    fun cancelledBecomesNoChangesWithoutInventingFailure() {
        val result = CourseImportResultMapper.map(ImportResult.Cancelled)

        assertTrue(result === CourseImportResult.NoChanges)
    }
}
