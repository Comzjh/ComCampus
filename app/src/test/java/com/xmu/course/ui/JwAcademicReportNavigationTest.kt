package com.xmu.course.ui

import com.xmu.course.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwAcademicReportNavigationTest {
    @Test
    fun academicReportRouteAndOfficialUrlAreCentralized() {
        assertEquals("jw_academic_report", AppRoutes.JW_ACADEMIC_REPORT)
        assertEquals(
            "https://jw.xmu.edu.cn/jwapp/sys/xywccx/*default/index.do?#/xywccx",
            AppLinks.JW_ACADEMIC_REPORT_URL,
        )
    }

    @Test
    fun autoRefreshReturnsOneStepOnlyWhileItsRouteIsCurrent() {
        var popCount = 0

        val popped = finishAcademicAutoRefreshIfCurrent(
            currentRoute = AppRoutes.JW_ACADEMIC_REPORT_AUTO,
            popBackStack = { popCount++; true },
        )

        assertTrue(popped)
        assertEquals(1, popCount)
    }

    @Test
    fun autoRefreshDoesNotChangeStackAfterUserHasLeftTheRoute() {
        var popCount = 0
        val popBackStack = { popCount++; true }

        assertFalse(finishAcademicAutoRefreshIfCurrent(AppRoutes.ACADEMIC_DATA_SOURCES, popBackStack))
        assertFalse(finishAcademicAutoRefreshIfCurrent(AppRoutes.GRADES_SANDBOX, popBackStack))
        assertFalse(finishAcademicAutoRefreshIfCurrent(null, popBackStack))
        assertEquals(0, popCount)
    }
}
