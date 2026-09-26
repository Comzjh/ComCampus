package com.xmu.course

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import android.webkit.WebView
import com.xmu.course.di.AppContainer
import com.xmu.course.ui.widget.WidgetUpdater

/** 应用进程入口：启动 Widget 数据监听。 */
class XmuCourseApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val isTronClassAuthProcess = currentProcessName()?.endsWith(":tronclass_auth") == true
        if (isTronClassAuthProcess) {
            // 必须在该进程第一次创建 WebView 前设置独立数据目录，隔离金智教务 Cookie。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix("tronclass_auth")
            }
        }
        // Robolectric 用例会频繁关闭各自的 Room 实例；不能让进程级 Widget observer
        // 持有测试数据库并在后台继续查询，否则会在下一用例触发失效连接。
        if (!isTronClassAuthProcess && !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            WidgetUpdater.start(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentProcessName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
}
