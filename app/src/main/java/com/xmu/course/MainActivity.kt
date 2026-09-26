package com.xmu.course

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xmu.course.data.DisplaySettings
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.ui.XmuCourseApp
import com.xmu.course.ui.theme.XmuCourseTheme
import com.xmu.course.ui.widget.EXTRA_WIDGET_COURSE_ID
import com.xmu.course.ui.widget.EXTRA_WIDGET_OPEN_TODO
import com.xmu.course.ui.widget.WidgetUpdater

/**
 * 应用唯一 Activity。
 *
 * 单 Activity + Navigation Compose 架构入口；Widget 点击会通过 extra 打开课程详情。
 */
class MainActivity : ComponentActivity() {

    private var widgetCourseId by mutableStateOf<Long?>(null)
    private var openTodoFromWidget by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplaySettings.load(this)
        TimetablePrefs.load(this)
        WidgetUpdater.scheduleMinuteRefresh(this)
        widgetCourseId = courseIdFromIntent(intent)
        openTodoFromWidget = intent?.getBooleanExtra(EXTRA_WIDGET_OPEN_TODO, false) == true
        enableEdgeToEdge()
        setContent {
            XmuCourseTheme {
                XmuCourseApp(
                    initialCourseId = widgetCourseId,
                    onInitialCourseConsumed = { widgetCourseId = null },
                    initialOpenTodo = openTodoFromWidget,
                    onInitialTodoConsumed = { openTodoFromWidget = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetCourseId = courseIdFromIntent(intent)
        openTodoFromWidget = intent.getBooleanExtra(EXTRA_WIDGET_OPEN_TODO, false)
    }

    private fun courseIdFromIntent(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_WIDGET_COURSE_ID, -1L)?.takeIf { it > 0L }
}
