package com.xmu.course.ui.timetable

import com.xmu.course.contracts.timetable.model.TimetableMatchModel
import com.xmu.course.domain.Course
import com.xmu.course.domain.cleanImportedCourseName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xmu.course.ui.components.AppButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.components.CourseColorSelector
import com.xmu.course.ui.components.DEFAULT_COURSE_COLOR
import com.xmu.course.ui.theme.AppShapes

/**
 * 课程详情 BottomSheet：完整信息 + 备注 + 颜色修改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    course: Course,
    tronCourseMatch: TimetableMatchModel? = null,
    isSkipped: Boolean,
    onDismiss: () -> Unit,
    onToggleSkipped: (Boolean) -> Unit,
    onColorChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = AppShapes.SheetTop,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                cleanImportedCourseName(course.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "第 ${course.startSection}-${course.startSection + course.duration - 1} 节 · " +
                    formatCourseWeeks(course.weeks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            HorizontalDivider(
                Modifier.padding(vertical = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            DetailRow("时间", TimeTableConfig.timeRange(startSection = course.startSection, duration = course.duration))
            DetailRow("教师", course.teacher.ifBlank { "未填写" })
            DetailRow("地点", course.location.ifBlank { "未填写" })
            DetailRow("星期", "星期${"日一二三四五六".substring(course.dayOfWeek % 7, course.dayOfWeek % 7 + 1)}")
            DetailRow("备注", course.note.ifBlank { "未填写" })
            DetailRow("状态", if (isSkipped) "已标记翘课" else "正常")
            tronCourseMatch?.let { match ->
                HorizontalDivider(
                    Modifier.padding(vertical = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text("畅课", style = MaterialTheme.typography.titleMedium)
                DetailRow("课程名称", match.name)
                DetailRow("教师", match.instructor.ifBlank { "未提供" })
                DetailRow("学期", match.semester.ifBlank { "未提供" })
                Text(
                    "课程资料、作业、通知入口将在后续版本接入",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            Button(
                onClick = { onToggleSkipped(!isSkipped) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp),
                shape = AppShapes.Button,
            ) {
                Text(if (isSkipped) "取消翘课" else "标记翘课")
            }
            Text(
                text = "课程颜色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
            CourseDetailColorSelector(
                selectedColor = course.color.ifBlank { DEFAULT_COURSE_COLOR },
                onColorChange = onColorChange,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
internal fun CourseDetailColorSelector(
    selectedColor: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) = CourseColorSelector(
    selectedColor = selectedColor,
    onColorChange = onColorChange,
    modifier = modifier,
    testTag = "course_detail_colors",
)

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}



/** 仅格式化显示，不扩展或改写实际周次集合。 */
internal fun formatCourseWeeks(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return "未设置周次"
    if (sorted.size == 1) return "第 ${sorted.first()} 周"
    val steps = sorted.zipWithNext { first, next -> next - first }
    return when {
        steps.all { it == 1 } -> "第 ${sorted.first()}–${sorted.last()} 周"
        steps.all { it == 2 } -> "第 ${sorted.first()}–${sorted.last()} 周（${if (sorted.first() % 2 == 0) "双" else "单"}周）"
        else -> "第 ${sorted.joinToString("、")} 周"
    }
}
