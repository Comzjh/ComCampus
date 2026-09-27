package com.xmu.course.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.xmu.course.MainActivity

const val EXTRA_WIDGET_COURSE_ID = "extra_widget_course_id"
const val EXTRA_WIDGET_OPEN_TODO = "extra_widget_open_todo"

fun buildTodoWidgetIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    putExtra(EXTRA_WIDGET_OPEN_TODO, true)
}

/** 打开课表页（今日课程 Widget 全卡点击）。 */
class OpenTimetableAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }
}

/** 打开课表页并定位指定课程详情（下一节课 Widget 点击）。 */
class OpenCourseAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val courseId = parameters.get(KEY_COURSE_ID)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_WIDGET_COURSE_ID, courseId ?: -1L)
        }
        context.startActivity(intent)
    }

    companion object {
        val KEY_COURSE_ID = ActionParameters.Key<Long>("xmu_course_id")
    }
}

/** 打开现有待办页面；Widget 不携带或处理待办完成操作。 */
class OpenTodoAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.startActivity(buildTodoWidgetIntent(context))
    }
}

/** Queue a session-aware manual refresh from the todo widget. */
class RefreshTodoWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        TodoWidgetSyncWork.enqueueManualRefresh(context)
    }
}
