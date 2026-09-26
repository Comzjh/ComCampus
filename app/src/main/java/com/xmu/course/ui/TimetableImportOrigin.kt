package com.xmu.course.ui

/**
 * 课表导入的发起页面（BUG-01 契约）。
 *
 * 真实入口只有两个：首页与课表页（设置与个人中心都没有课表导入入口）。
 * 导入成功后的回跳目标由发起页显式决定，不再依赖"课表是否恰好在返回栈里"。
 */
enum class TimetableImportOrigin(val route: String) {
    HOME(AppRoutes.HOME),
    TIMETABLE(AppRoutes.TIMETABLE),
    ;

    /** 带发起页参数的导入页路由。 */
    val importRoute: String
        get() = "${AppRoutes.IMPORT}?$ARGUMENT=${name.lowercase()}"

    /** 带发起页参数的教务 WebView 路由（导入 → 抓取 同一发起页语义）。 */
    val webviewRoute: String
        get() = "${AppRoutes.WEBVIEW}?$ARGUMENT=${name.lowercase()}"

    companion object {
        /** 路由参数名：import?origin=home / webview?origin=home。 */
        const val ARGUMENT = "origin"

        /** 参数缺失或无法识别时保守回落到课表页，保持既有默认行为。 */
        val DEFAULT: TimetableImportOrigin
            get() = TIMETABLE

        fun fromArgument(raw: String?): TimetableImportOrigin =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TIMETABLE
    }
}
