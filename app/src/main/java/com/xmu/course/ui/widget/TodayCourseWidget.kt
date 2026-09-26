package com.xmu.course.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 今日课程 Widget（4×2）。
 *
 * 数据链路：Room -> WidgetRepository -> provideGlance -> Glance UI。
 * 不修改课程/解析/布局引擎；只读取当前课表和今日课程。
 */
class TodayCourseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetRepository.loadToday(context)
        provideContent {
            TodayCourseContent(data)
        }
    }
}

class TodayCourseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayCourseWidget()
}

@Composable
fun TodayCourseContent(
    data: WidgetTimetableData,
    style: WidgetBackgroundStyle = WidgetTheme.defaultStyle,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.background(style))
            .clickable(actionRunCallback(OpenTimetableAction::class.java)),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "ComCampus",
                style = widgetTextStyle(
                    color = WidgetTheme.textPrimary,
                    size = 13.sp,
                    weight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "第${data.week}周 · ${data.dayOfWeek.toChineseDay()}",
                style = widgetTextStyle(
                    color = WidgetTheme.textSecondary,
                    size = 11.sp,
                    weight = FontWeight.Medium,
                ),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))

            if (data.courses.isEmpty()) {
                Text(
                    text = "今日无课程",
                    style = widgetTextStyle(
                        color = WidgetTheme.textSecondary,
                        size = 12.sp,
                        weight = FontWeight.Medium,
                    ),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(data.courses) { course ->
                        TodayCourseItem(course)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCourseItem(course: WidgetCourse) {
    val backgroundColor = if (course.isSkipped) {
        Color(0x14FFFFFF)
    } else {
        Color(0x26FFFFFF)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .background(ColorProvider(backgroundColor))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = course.startTime,
            style = widgetTextStyle(
                color = WidgetTheme.secondaryTextColor(course.isSkipped),
                size = 11.sp,
                weight = FontWeight.Medium,
            ),
        )
        Spacer(modifier = GlanceModifier.width(9.dp))
        Column {
            Text(
                text = course.name,
                style = widgetTextStyle(
                    color = WidgetTheme.textColor(course.isSkipped),
                    size = 12.sp,
                    weight = FontWeight.Bold,
                ),
            )
            if (course.location.isNotBlank()) {
                Text(
                    text = "@${course.location}",
                    style = widgetTextStyle(
                        color = WidgetTheme.secondaryTextColor(course.isSkipped),
                        size = 10.sp,
                        weight = FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun widgetTextStyle(
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
): TextStyle = TextStyle(
    color = ColorProvider(color),
    fontSize = size,
    fontWeight = weight,
)

internal fun Int.toChineseDay(): String = when (this) {
    1 -> "星期一"
    2 -> "星期二"
    3 -> "星期三"
    4 -> "星期四"
    5 -> "星期五"
    6 -> "星期六"
    7 -> "星期日"
    else -> "星期"
}
