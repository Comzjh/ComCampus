package com.xmu.course.adapter.jw

/**
 * JW 只读数据端点白名单与写端点硬拒绝清单。
 *
 * 所有远端访问必须先通过本策略：
 * - 只允许 [JwReadEndpoint] 中逐条登记、且经集成包实测为只读的端点；
 * - 写端点（重算/提交申请/发邮件/打印/报表）无论以何种形式出现一律拒绝；
 * - 请求只允许在官方 jw.xmu.edu.cn 页面上下文内执行。
 *
 * MANUAL_ONLY：调用方只能是用户显式触发的刷新流程；禁止后台/定时访问。
 */
object JwEndpointPolicy {

    /** 官方教务域；WebView 页面 origin 必须精确等于该 host。 */
    const val TRUSTED_HOST = "jw.xmu.edu.cn"

    /** 写端点拒绝模式：路径包含任一子串即永久禁止（大小写不敏感）。 */
    private val WRITE_DENY_PATTERNS = listOf(
        "bysc.do", // xywccx 后台重新计算（写库+重算）
        "byscjd.do", // 重算进度轮询：仅配合 bysc 使用，本里程碑整体拒绝
        "saveZmsq.do", // 提交真实证明申请
        "sendMail.do", // 发送邮件
        "/zzdy/", // 打印/登记族
        "recordPrint", // 打印登记
        "printBy", // printByXh / printByCode
        "getZmlist", // 遗留调试打印接口
        "frReport2", // 帆软报表渲染/生成
        "downZm.do", // 证明文件下载（二进制流，本里程碑不接）
        "viewZm.do", // 证明预览（同上）
    )

    /** 端点路径是否命中写端点拒绝清单。 */
    fun isWriteDenied(path: String): Boolean {
        val lower = path.lowercase()
        return WRITE_DENY_PATTERNS.any { lower.contains(it.lowercase()) }
    }

    /**
     * 路径是否允许发起只读请求。
     *
     * 双重保险：先拒绝写模式，再要求精确命中 [JwReadEndpoint] 登记路径；
     * 未知路径一律 false，不做前缀模糊匹配。
     */
    fun isReadAllowed(path: String): Boolean =
        !isWriteDenied(path) && JwReadEndpoint.values().any { it.path == path }

    /** WebView 当前页面 origin 是否为可信官方域（精确 host 匹配，端口默认 443）。 */
    fun isTrustedOrigin(origin: String?): Boolean {
        val normalized = origin?.trim()?.lowercase()?.removeSuffix("/")
        return normalized == "https://$TRUSTED_HOST" || normalized == "https://$TRUSTED_HOST:443"
    }
}

/** 请求方言：三源参数体系严格隔离，禁止互相复用请求构造。 */
enum class JwRequestDialect {
    /** xywccx：EMAP datatable，page/rows，字段平铺。 */
    XYWCCX_PAGE_ROWS,

    /** cjcx：querySetting JSON + %2Aorder 排序串 + pageSize/pageNumber。 */
    CJCX_QUERY_SETTING,

    /** zmsqxmu：pageSize/pageNumber/order 方言（传错参数会挂起）。 */
    ZMSQ_PAGE_SIZE,
}

/** HTTP 方法：GET 仅用于集成包实测标注为 GET 的 xywccx 汇总端点。 */
enum class JwHttpMethod { GET, POST }

/** 响应信封类型：EMAP 家族要求 code/datas；gsapp 返回原生 JSON 对象。 */
enum class JwResponseEnvelope { EMAP, PLAIN_JSON }

/**
 * 已登记并通过集成包实测的只读端点。
 *
 * 新增端点必须先有集成包/官方页面的只读证据，再在此登记；
 * 未登记路径在 [JwEndpointPolicy] 层面直接拒绝。
 */
enum class JwReadEndpoint(
    val path: String,
    val dialect: JwRequestDialect,
    val method: JwHttpMethod,
    val envelope: JwResponseEnvelope = JwResponseEnvelope.EMAP,
    /** Phase H 实测：xywccx 家族必须经 BH_UTILS.doSyncAjax 的 jw_security 签名通道；无符号 fetch 一律 403。 */
    val signed: Boolean = false,
) {
    // —— xywccx（page/rows 方言）——
    // Phase H 实测：真实 API 路径不含页面 UI 段的 *default 标记（WIS_EMAP_SERV.getAbsPath 实测）。
    XYWCCX_CURRENT_SEMESTER(
        "/jwapp/sys/xywccx/modules/xywccx/cxdqxnxq.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.POST,
        signed = true,
    ),
    XYWCCX_PLANS(
        "/jwapp/sys/xywccx/modules/xywccx/grpyfacx.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.POST,
        signed = true,
    ),
    XYWCCX_COMPLETION_SNAPSHOT(
        "/jwapp/sys/xywccx/modules/xywccx/cxxsscfa.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.POST,
        signed = true,
    ),
    XYWCCX_PLAN_SEMESTER_TOTAL(
        "/jwapp/sys/xywccx/modules/xywccx/cxfakzyxxfgj.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.GET,
        signed = true,
    ),
    XYWCCX_OUTSIDE_PLAN_SUMMARY(
        "/jwapp/sys/xywccx/modules/xywccx/cxfawxfms.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.GET,
        signed = true,
    ),
    XYWCCX_COURSE_POOL(
        "/jwapp/sys/xywccx/ByshController/queryKzkcXsyx.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.POST,
        signed = true,
    ),
    /** 本学期课表课程名（gsapp wdkbapp；与课池按 KCDM==KCH join）。 */
    GSAPP_SEMESTER_COURSES(
        // Phase H 实测（2026-09-20）：真实 API 路径无 *default/无 modules 段；
        // gsapp 会话经 WebView 顶层导航建立后，无符号 fetch 即 200（success|pkjgList|ttbList）。
        "/gsapp/sys/wdkbapp/wdkcb/queryXspkjg.do",
        JwRequestDialect.XYWCCX_PAGE_ROWS,
        JwHttpMethod.POST,
        JwResponseEnvelope.PLAIN_JSON,
    ),

    // —— cjcx（querySetting 方言）——
    CJCX_STUDENT_GRADES(
        "/jwapp/sys/cjcx/modules/cjcx/xscjcx.do",
        JwRequestDialect.CJCX_QUERY_SETTING,
        JwHttpMethod.POST,
    ),

    // —— zmsqxmu（pageSize/pageNumber 方言；本里程碑仅数据层，不接 UI）——
    ZMSQ_APPLICATION_LIST(
        "/jwapp/sys/zmsqxmu/modules/xszmsq/xszmsqcx.do",
        JwRequestDialect.ZMSQ_PAGE_SIZE,
        JwHttpMethod.POST,
    ),
    ZMSQ_APPLICABLE_TYPES(
        "/jwapp/sys/zmsqxmu/modules/xszmsq/dsqzmcx.do",
        JwRequestDialect.ZMSQ_PAGE_SIZE,
        JwHttpMethod.POST,
    ),
}
