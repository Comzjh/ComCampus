package com.xmu.course.adapter.jw

/**
 * JW 入口契约：Feature 不感知 jw.xmu.edu.cn URL、AppLinks 常量、WiseduAuthConfig、
 * WebView 或 CookieManager。
 *
 * MANUAL_ONLY：只由用户主动触发打开页面；无自动导航、无后台访问。
 *
 * Auth 边界（本轮仅规划，不实现）：
 * - JW 与 Wisedu 共享 jw.xmu.edu.cn 的 SSO/Cookie 域（主进程 CookieManager），本轮不拆分；
 * - 未来方向：core auth contract + adapter 实现，使 Feature 完全不接触
 *   CookieManager/WebView/JW URL；凭据始终只存在于官方 WebView。
 *
 * @see com.xmu.course.contracts.campusservice.CampusServiceProvider 多服务统一入口契约
 */
@Deprecated(
    message = "单入口契约；多服务请迁移到 CampusServiceProvider",
    replaceWith = ReplaceWith("CampusServiceProvider"),
)
fun interface JwGateway {
    /** 用户主动打开官方学业完成查询页面。 */
    fun openAcademicReport()
}