package com.xmu.course.ui

import com.xmu.course.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusServiceNavigationTest {
    @Test
    fun campusServiceRoutesAreCentralized() {
        assertEquals("campus_service", AppRoutes.CAMPUS_SERVICE)
        assertEquals("jw_certificate", AppRoutes.JW_CERTIFICATE)
    }

    @Test
    fun certificateOfficialUrlIsCentralized() {
        assertEquals(
            "https://jw.xmu.edu.cn/jwapp/sys/zmsqxmu/*default/index.do",
            AppLinks.JW_CERTIFICATE_URL,
        )
    }
}