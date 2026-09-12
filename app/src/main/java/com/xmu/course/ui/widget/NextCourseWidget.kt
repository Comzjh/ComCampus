package com.xmu.course.ui.widget

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 下一节课 Widget（2×2）。
 *
 * 点击进入对应课程详情；倒计时按分钟粒度展示。
 */
class NextCourseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetRepository.loadToday(context)
        val next = WidgetRepository.findNextCourse(data.courses, data.nowMinuteOfDay)
        provideContent {
            NextCourseContent(next, data.nowMinuteOfDay)
        }
    }
}

class NextCourseWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MINUTE_TICK) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                WidgetUpdater.updateAll(context)
            }
            WidgetUpdater.scheduleMinuteRefresh(context)
        }
    }

    companion object {
        const val ACTION_MINUTE_TICK = "com.xmu.course.widget.MINUTE_TICK"
    }

    override val glanceAppWidget: GlanceAppWidget = NextCourseWidget()
}

@Composable
fun NextCourseContent(
    next: WidgetCourse?,
    nowMinuteOfDay: Int,
    style: WidgetBackgroundStyle = WidgetTheme.defaultStyle,
) {
    val title = if (next == null) "今日无课程" else "下一节课"
    val time = next?.startTime ?: "--:--"
    val name = next?.name ?: "休息一下"
    val location = next?.location?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""
    val countdown = WidgetRepository.countdownText(nowMinuteOfDay, next)
    val courseId = next?.id ?: -1L
    val action = if (next != null) {
        actionRunCallback(
            OpenCourseAction::class.java,
            actionParametersOf(OpenCourseAction.KEY_COURSE_ID to courseId),
        )
    } else {
        actionRunCallback(OpenTimetableAction::class.java)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.background(style))
            .clickable(action),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = widgetTextStyle(
                    color = WidgetTheme.textSecondary,
                    size = 11.sp,
                    weight = FontWeight.Medium,
                ),
            )
            Spacer(modifier = GlanceModifier.height(5.dp))
            Text(
                text = time,
                style = widgetTextStyle(
                    color = WidgetTheme.textPrimary,
                    size = 18.sp,
                    weight = FontWeight.Bold,
                ),
            )
            Text(
                text = name,
                style = widgetTextStyle(
                    color = WidgetTheme.textPrimary,
                    size = 13.sp,
                    weight = FontWeight.Bold,
                ),
            )
            if (location.isNotBlank()) {
                Text(
                    text = location,
                    style = widgetTextStyle(
                        color = WidgetTheme.textSecondary,
                        size = 11.sp,
                        weight = FontWeight.Normal,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = countdown,
                style = widgetTextStyle(
                    color = if (next?.isSkipped == true) WidgetTheme.disabledText else WidgetTheme.textSecondary,
                    size = 11.sp,
                    weight = FontWeight.Medium,
                ),
            )
        }
    }
}


