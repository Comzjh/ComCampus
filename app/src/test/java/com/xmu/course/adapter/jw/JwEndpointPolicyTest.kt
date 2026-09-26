package com.xmu.course.adapter.jw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 只读白名单与写端点硬拒绝清单的行为。 */
class JwEndpointPolicyTest {

    @Test
    fun registeredReadOnlyEndpointsAreAllowed() {
        JwReadEndpoint.values().forEach { endpoint ->
            assertTrue("expected allowed: ${endpoint.path}", JwEndpointPolicy.isReadAllowed(endpoint.path))
        }
    }

    @Test
    fun writeEndpointsAreDeniedEvenWithSimilarPaths() {
        val writePaths = listOf(
            "/jwapp/sys/xywccx/*default/modules/xywccx/xywccx/bysc.do",
            "/jwapp/sys/xywccx/*default/modules/xywccx/byscjd.do",
            "/jwapp/sys/zmsqxmu/modules/xszmsq/saveZmsq.do",
            "/jwapp/sys/zmsqxmu/modules/xszmsq/sendMail.do",
            "/jwapp/sys/zmsqxmu/modules/zzdy/recordPrint.do",
            "/jwapp/sys/zmsqxmu/modules/zzdy/printByXh.do",
            "/jwapp/sys/zmsqxmu/modules/zzdy/printByCode.do",
            "/jwapp/sys/zmsqxmu/modules/zzdy/getZmlist.do",
            "/sys/frReport2/show.do",
            "/jwapp/sys/zmsqxmu/modules/xszmsq/downZm.do",
            "/jwapp/sys/zmsqxmu/modules/xszmsq/viewZm.do",
        )
        writePaths.forEach { path ->
            assertTrue("expected write-denied: $path", JwEndpointPolicy.isWriteDenied(path))
            assertFalse("write endpoint must never be read-allowed: $path", JwEndpointPolicy.isReadAllowed(path))
        }
    }

    @Test
    fun unknownPathsAreDenied() {
        listOf(
            "",
            "/jwapp/sys/xywccx/*default/modules/xywccx/unknown.do",
            "/jwapp/sys/cjcx/modules/cjcx/otherdo.do",
            "https://evil.example.com/jwapp/sys/cjcx/modules/cjcx/xscjcx.do",
        ).forEach { path ->
            assertFalse("expected denied: $path", JwEndpointPolicy.isReadAllowed(path))
        }
    }

    @Test
    fun onlyExactOfficialOriginIsTrusted() {
        assertTrue(JwEndpointPolicy.isTrustedOrigin("https://jw.xmu.edu.cn"))
        assertTrue(JwEndpointPolicy.isTrustedOrigin("https://jw.xmu.edu.cn:443"))
        assertFalse(JwEndpointPolicy.isTrustedOrigin("http://jw.xmu.edu.cn"))
        assertFalse(JwEndpointPolicy.isTrustedOrigin("https://ids.xmu.edu.cn"))
        assertFalse(JwEndpointPolicy.isTrustedOrigin("https://jw.xmu.edu.cn.evil.example"))
        assertFalse(JwEndpointPolicy.isTrustedOrigin(null))
    }

    @Test
    fun dialectsAreSeparatedPerSource() {
        assertTrue(JwReadEndpoint.XYWCCX_COURSE_POOL.dialect == JwRequestDialect.XYWCCX_PAGE_ROWS)
        assertTrue(JwReadEndpoint.CJCX_STUDENT_GRADES.dialect == JwRequestDialect.CJCX_QUERY_SETTING)
        assertTrue(JwReadEndpoint.ZMSQ_APPLICATION_LIST.dialect == JwRequestDialect.ZMSQ_PAGE_SIZE)
    }
}
