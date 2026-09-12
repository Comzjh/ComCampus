package com.xmu.course.ui.timetable

import androidx.compose.foundation.background
import com.xmu.course.domain.Course
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 课程详情可改的颜色。 */
private val EDITABLE_COLORS = listOf(
    "#FAAC8F", "#FDCF93", "#93D36E", "#7FD4E0",
    "#A79FE1", "#F49BC1", "#8FBCFA", "#E8C877",
)

/**
 * 课程详情 BottomSheet：完整信息 + 备注 + 颜色修改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    course: Course,
    isSkipped: Boolean,
    onDismiss: () -> Unit,
    onToggleSkipped: (Boolean) -> Unit,
    onColorChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "第 ${course.startSection}-${course.startSection + course.duration - 1} 节 · " +
                    "周 ${course.weeks.sorted().joinToString(",")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            DetailRow("时间", TimeTableConfig.timeRange(startSection = course.startSection, duration = course.duration))
            DetailRow("教师", course.teacher.ifBlank { "未填写" })
            DetailRow("地点", course.location.ifBlank { "未填写" })
            DetailRow("星期", "星期${"日一二三四五六".substring(course.dayOfWeek % 7, course.dayOfWeek % 7 + 1)}")
            DetailRow("备注", course.note.ifBlank { "未填写" })
            DetailRow("状态", if (isSkipped) "已标记翘课" else "正常")
            Button(
                onClick = { onToggleSkipped(!isSkipped) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(if (isSkipped) "取消翘课" else "标记翘课")
            }
            Text(
                text = "课程颜色",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EDITABLE_COLORS.forEach { hex ->
                    val selected = course.color.equals(hex, ignoreCase = true)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color(android.graphics.Color.parseColor(hex)),
                                CircleShape,
                            )
                            .clickable { onColorChange(hex) },
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        if (selected) {
                            Text("✓", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}


