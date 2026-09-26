package com.xmu.course.adapter.jw

/**
 * gsapp（我的课表）子应用会话引导的纯 URL 落点判定。
 *
 * Phase H 实测：gsapp 是独立子应用，页面内 fetch/iframe/签名通道均无法建立其会话，
 * 只有 WebView 顶层导航能完成 302→CAS→ticket 回调。本对象只做落点分类，
 * 不读取/导出任何 Cookie、JSESSIONID 或 Ticket；引导失败一律 fail-closed。
 */
object JwGsappSessionPriming {

    /** gsapp wdkbapp 官方引导页（只读页面，无写副作用）。 */
    const val PRIMING_URL = "https://jw.xmu.edu.cn/gsapp/sys/wdkbapp/*default/index.do"

    private const val GSAPP_PAGE_PREFIX = "https://jw.xmu.edu.cn/gsapp/sys/wdkbapp/"

    /** 引导阶段一次页面完成后的落点。 */
    enum class Landing {
        /** 已落在 gsapp 官方页面：会话建立成功，可返回学业查询页。 */
        GsappReady,

        /** 已落在学业查询页本身：引导视为结束，不再二次导航。 */
        ReportPageReached,

        /** 其他页面（含官方登录页）：停下等待用户手动处理，绝不自动重试。 */
        Hold,
    }

    /** 顶层导航落点分类；reportUrl 为学业查询页地址（比较时忽略 query/fragment）。 */
    fun landingFor(url: String?, reportUrl: String): Landing = when {
        url == null -> Landing.Hold
        url.startsWith(GSAPP_PAGE_PREFIX) -> Landing.GsappReady
        url.startsWith(reportUrl.substringBefore('?')) -> Landing.ReportPageReached
        else -> Landing.Hold
    }
}
