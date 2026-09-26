package com.xmu.course.ui.tronclass.auth

import android.net.Uri

/** 只使用 host/path 判断回到畅课入口，不读取或记录 URL 参数。 */
object TronAuthPagePolicy {
    const val TRONCLASS_HOST = "lnt.xmu.edu.cn"
    private const val COURSES_PATH = "/user/courses"

    fun isCoursesPage(uri: Uri?): Boolean =
        uri?.host.equals(TRONCLASS_HOST, ignoreCase = true) &&
            uri?.path.orEmpty().trimEnd('/') == COURSES_PATH
}
