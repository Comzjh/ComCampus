package com.xmu.course

import android.app.Application
import android.os.Build
import com.xmu.course.ui.widget.WidgetUpdater

/** 应用进程入口：启动 Widget 数据监听。 */
class XmuCourseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Robolectric 用例会频繁关闭各自的 Room 实例；不能让进程级 Widget observer
        // 持有测试数据库并在后台继续查询，否则会在下一用例触发失效连接。
        if (!Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            WidgetUpdater.start(this)
        }
    }
}
