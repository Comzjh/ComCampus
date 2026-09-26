package com.xmu.course.adapter.jw

import java.net.URLEncoder

/**
 * xywccx 请求方言：EMAP datatable，`page/rows`，字段平铺（勿包 param JSON）。
 *
 * 该方言与 cjcx（querySetting）和 zmsqxmu（pageSize/pageNumber）严格隔离，
 * 混用会导致挂起 / 403 / 无效响应（集成包 README §3/§4 实测记录）。
 *
 * 隐私：学号一律使用 `{{XH}}` 占位符，由页面内 JS 替换；Kotlin 不接触学生身份。
 */
object JwXywccxDialect {

    /** 课程池一次拉全的 rows（集成包实测 rows=200 曾 504，500 已验证）。 */
    const val POOL_ROWS = 500

    /** 方案外已结课过滤行数（实测极少，100 足够）。 */
    const val FAWKC_ROWS = 100

    private val PlanCodePattern = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val TermPattern = Regex("^[0-9]{4}(-[0-9]{4})?-[0-9]{1,2}$|^[0-9]{5}$")

    /** 当前学期代码。 */
    fun currentSemesterBody(): String = ""

    /** 个人培养方案列表。 */
    fun plansBody(): String = "XH=$XH_PLACEHOLDER"

    /** 已计算完成度快照（WCXF/CZSJ）。 */
    fun completionSnapshotBody(): String = "XH=$XH_PLACEHOLDER&SCLBDM=04"

    /**
     * 方案级本学期已选学分（GET，无 KZH）。
     *
     * @param planCode grpyfacx 返回的 PYFADM；仅接受稳定标识字符集。
     * @param semesterCode jwapp 学期代码（如 2026-2027-1）。
     */
    fun planSemesterTotalQuery(planCode: String, semesterCode: String): String {
        require(planCode.matches(PlanCodePattern)) { "invalid plan code" }
        require(semesterCode.matches(TermPattern)) { "invalid semester code" }
        return "XH=$XH_PLACEHOLDER&PYFADM=$planCode&BYNJDM=-&PCDM=-&SCLBDM=04" +
            "&XNXQDM=${encode(semesterCode)}"
    }

    /** 方案外学分汇总（GET）。 */
    fun outsidePlanSummaryQuery(planCode: String): String {
        require(planCode.matches(PlanCodePattern)) { "invalid plan code" }
        return "XH=$XH_PLACEHOLDER&PYFADM=$planCode&BYNJDM=-&PCDM=-"
    }

    /** 441 行课程池（KCH→XF 字典 + 已修课）。 */
    fun coursePoolBody(planCode: String): String = poolBody(planCode, withFawkc = false)

    /** 方案外已结课过滤（键名必须是 KZH，不是 KCH）。 */
    fun outsidePlanCompletedBody(planCode: String): String = poolBody(planCode, withFawkc = true)

    /**
     * gsapp 本学期课表课程名。
     *
     * @param gsappSemesterCode gsapp 学期码（如 20261），与 jwapp 学期码不同体系，
     *   由 [com.xmu.course.data.academiccompletion.XywccxSnapshotAssembler.mapGsappSemesterCode] 转换。
     */
    fun semesterCoursesBody(gsappSemesterCode: String): String {
        require(gsappSemesterCode.matches(TermPattern)) { "invalid gsapp semester code" }
        // XH 可空：服务端按会话取人（集成包 §2.5 实测）。
        return "XNXQDM=${encode(gsappSemesterCode)}&XH="
    }

    private fun poolBody(planCode: String, withFawkc: Boolean): String {
        require(planCode.matches(PlanCodePattern)) { "invalid plan code" }
        val base = "action=cxscfakzkc_xsyx&pagePath=${encode("/modules/xywccx.do")}" +
            "&page=1&rows=${if (withFawkc) FAWKC_ROWS else POOL_ROWS}" +
            "&XH=$XH_PLACEHOLDER&PYFADM=$planCode&BYNJDM=-&PCDM=-&SCLBDM=04"
        return if (withFawkc) "$base&KZH=FAWKC" else base
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** 页面内替换的学号占位符。 */
    const val XH_PLACEHOLDER = "{{XH}}"
}
