package com.xmu.course.adapter.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** zmsqxmu 只读方言：pageSize/pageNumber/order，及官方实测的编码细节。 */
class ZmsqxmuRequestsTest {

    @Test
    fun applicationListUsesZmsqDialectNotPageRows() {
        val body = ZmsqxmuRequests.applicationListBody(pageNumber = 2)
        assertEquals("order=-SQSJ&pageSize=10&pageNumber=2", body)
        assertFalse("禁止混入 xywccx 的 page/rows 方言", body.contains("rows="))
        assertFalse(body.contains("page="))
        assertFalse(body.contains("querySetting="))
    }

    @Test
    fun applicableTypesEncodesStarOrderAsDocumented() {
        val body = ZmsqxmuRequests.applicableTypesBody()
        assertEquals("%2Aorder=+WID&pageSize=200&pageNumber=1", body)
        assertFalse("顶层不得出现未编码的 *order 键", body.startsWith("*order="))
    }

    @Test
    fun rejectOutOfRangePaging() {
        assertThrows(IllegalArgumentException::class.java) {
            ZmsqxmuRequests.applicationListBody(pageNumber = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ZmsqxmuRequests.applicationListBody(pageSize = 0)
        }
    }

    @Test
    fun bodiesNeverCarryStudentIdentity() {
        assertFalse(ZmsqxmuRequests.applicationListBody().contains("XH="))
        assertFalse(ZmsqxmuRequests.applicableTypesBody().contains("{{XH}}"))
    }
}
