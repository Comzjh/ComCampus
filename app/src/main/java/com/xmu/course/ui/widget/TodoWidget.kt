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
import androidx.glance.unit.ColorProvider

/** Todo Widget：只读 Room 中的未完成待办，不发起网络请求。 */
class TodoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetRepository.loadTodos(context)
        provideContent {
            TodoWidgetContent(data)
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

@Composable
fun TodoWidgetContent(
    data: WidgetTodoData,
    style: WidgetBackgroundStyle = WidgetTheme.defaultStyle,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.background(style))
            .clickable(actionRunCallback(OpenTodoAction::class.java)),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "待办 · ${data.totalUnfinished}",
                style = widgetTextStyle(
                    color = WidgetTheme.textPrimary,
                    size = 13.sp,
                    weight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(5.dp))
            if (data.items.isEmpty()) {
                Text(
                    text = "暂无待办",
                    style = widgetTextStyle(
                        color = WidgetTheme.textSecondary,
                        size = 12.sp,
                        weight = FontWeight.Medium,
                    ),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(data.items) { item ->
                        TodoWidgetRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoWidgetRow(item: WidgetTodoItem) {
    val supportingText = buildString {
        item.courseName?.takeIf { it.isNotBlank() }?.let {
            append(WidgetRepository.truncateWidgetText(it, 22))
            append(" · ")
        }
        append(item.deadlineText)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .background(ColorProvider(Color(0x26FFFFFF)))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.countdownText?.let { prefix ->
                    Text(
                        text = prefix,
                        style = widgetTextStyle(
                            color = WidgetTheme.todoUrgencyColor(item.urgency),
                            size = 10.sp,
                            weight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                Text(
                    text = WidgetRepository.truncateWidgetText(
                        item.title,
                        if (item.countdownText == null) 28 else 24,
                    ),
                    style = widgetTextStyle(
                        color = WidgetTheme.textPrimary,
                        size = 12.sp,
                        weight = FontWeight.Bold,
                    ),
                )
            }
            Text(
                text = WidgetRepository.truncateWidgetText(supportingText, 36),
                style = widgetTextStyle(
                    color = WidgetTheme.textSecondary,
                    size = 10.sp,
                    weight = FontWeight.Normal,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.width(1.dp))
    }
}
