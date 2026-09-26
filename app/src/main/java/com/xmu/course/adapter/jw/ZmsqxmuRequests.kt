package com.xmu.course.adapter.jw

/**
 * zmsqxmu 只读请求方言：`pageSize/pageNumber/order`。
 *
 * 与 xywccx（page/rows）和 cjcx（querySetting）严格隔离：
 * 实测传错参数体系会导致后端挂起（集成包 zmsqxmu.md §4）。
 *
 * 本对象只提供列表类只读请求；证明详情/可打印份数/下载/预览/提交
 * 均不在白名单（viewZm/downZm/saveZmsq/sendMail/zzdy 打印族
 * 已在 [JwEndpointPolicy] 写端点拒绝清单中硬拒绝）。
 *
 * 隐私：请求体不含学号；服务端按会话识别学生。
 */
object ZmsqxmuRequests {

    /** 申请记录列表默认页大小（官方页面初始请求即 10）。 */
    const val DEFAULT_LIST_PAGE_SIZE = 10

    /** 可申请证明清单一次拉全（官方页面实测值 200 行，当前仅 14-15 项）。 */
    const val TYPES_PAGE_SIZE = 200

    /**
     * 我的证明申请记录列表（按申请时间 SQSJ 倒序）。
     *
     * ⚠️ 该端点所在模块有 504 高发史：调用方须配合指数退避重试
     * （15s/30s/60s，上限 3 次）；本里程碑仅在用户手动刷新路径使用。
     */
    fun applicationListBody(pageNumber: Int = 1, pageSize: Int = DEFAULT_LIST_PAGE_SIZE): String {
        require(pageNumber >= 1) { "pageNumber must be >= 1" }
        require(pageSize in 1..TYPES_PAGE_SIZE) { "pageSize out of range" }
        return "order=-SQSJ&pageSize=$pageSize&pageNumber=$pageNumber"
    }

    /**
     * 可申请证明类型清单。
     *
     * 排序键的 `*` 必须编码为 `%2A`；值按官方页面原始请求保留 `+WID` 原文
     * （实测该字节序列即服务端可解析的 WID 升序）。
     * 注：服务端实测忽略 pageNumber 且 totalSize 有 ±1 怪癖，故不做行数对账拒绝。
     */
    fun applicableTypesBody(): String =
        "%2Aorder=+WID&pageSize=$TYPES_PAGE_SIZE&pageNumber=1"
}
