package com.xmu.course.contracts.campusservice

/**
 * 校园服务入口契约：Feature 只依赖此契约，不感知具体学校系统、URL、WebView 或认证细节。
 *
 * MANUAL_ONLY：所有入口必须由用户主动触发；无自动导航、无后台访问。
 * 当前由 app 内 XmuJwAdapter 提供实现；多服务清单与按 id 打开请使用 CampusServiceProvider。
 */
fun interface CampusServiceLauncher {
    /** 用户主动打开官方学业完成查询页面。 */
    fun openAcademicReport()
}
