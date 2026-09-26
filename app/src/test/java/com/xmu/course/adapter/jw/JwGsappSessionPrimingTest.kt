package com.xmu.course.adapter.jw

import com.xmu.course.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Test

/** gsapp 会话引导落点判定：仅 gsapp 页/查询页结束引导，其余一律等待用户。 */
class JwGsappSessionPrimingTest {

    @Test
    fun landingOnGsappPageEndsPrimingWithReturn() {
        assertEquals(
            JwGsappSessionPriming.Landing.GsappReady,
            JwGsappSessionPriming.landingFor(
                JwGsappSessionPriming.PRIMING_URL,
                AppLinks.JW_ACADEMIC_REPORT_URL,
            ),
        )
        assertEquals(
            JwGsappSessionPriming.Landing.GsappReady,
            JwGsappSessionPriming.landingFor(
                "https://jw.xmu.edu.cn/gsapp/sys/wdkbapp/*default/index.do",
                AppLinks.JW_ACADEMIC_REPORT_URL,
            ),
        )
    }

    @Test
    fun landingOnReportPageEndsPrimingWithoutNavigation() {
        assertEquals(
            JwGsappSessionPriming.Landing.ReportPageReached,
            JwGsappSessionPriming.landingFor(
                "https://jw.xmu.edu.cn/jwapp/sys/xywccx/*default/index.do",
                AppLinks.JW_ACADEMIC_REPORT_URL,
            ),
        )
        assertEquals(
            JwGsappSessionPriming.Landing.ReportPageReached,
            JwGsappSessionPriming.landingFor(
                AppLinks.JW_ACADEMIC_REPORT_URL,
                AppLinks.JW_ACADEMIC_REPORT_URL,
            ),
        )
    }

    @Test
    fun loginOrUnknownPagesHoldForUser() {
        listOf(
            "https://cas.xmu.edu.cn/cas/login?service=https%3A%2F%2Fjw.xmu.edu.cn%2Fgsapp",
            "https://jw.xmu.edu.cn/error.jsp",
            "about:blank",
            null,
        ).forEach { url ->
            assertEquals(
                "expected hold: $url",
                JwGsappSessionPriming.Landing.Hold,
                JwGsappSessionPriming.landingFor(url, AppLinks.JW_ACADEMIC_REPORT_URL),
            )
        }
    }

    @Test
    fun primingUrlIsGsappReadOnlyPage() {
        assertEquals(
            "https://jw.xmu.edu.cn/gsapp/sys/wdkbapp/*default/index.do",
            JwGsappSessionPriming.PRIMING_URL,
        )
        assertEquals(
            false,
            JwEndpointPolicy.isWriteDenied("/gsapp/sys/wdkbapp/*default/index.do"),
        )
    }
}
