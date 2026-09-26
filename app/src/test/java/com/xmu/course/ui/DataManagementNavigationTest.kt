package com.xmu.course.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DataManagementNavigationTest {
    @Test
    fun dataManagementRoutesAreCentralized() {
        assertEquals("data_management", AppRoutes.DATA_MANAGEMENT)
        assertEquals("data_management/{sourceId}", AppRoutes.DATA_SOURCE_DETAIL)
    }
}
