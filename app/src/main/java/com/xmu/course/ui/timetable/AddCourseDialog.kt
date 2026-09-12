package com.xmu.course.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xmu.course.data.CourseRepository
import com.xmu.course.domain.Course
import com.xmu.course.domain.CourseSource
import com.xmu.course.parser.WeekPatternParser

/**
 * 手动添加课程对话框。
 *
 * 周数输入支持金智格式（1-16周 / 单周 / 双周 / 混合列表），复用 WeekPatternParser。
 */
@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("星期一") }
    var startSectionText by remember { mutableStateOf("1") }
    var endSectionText by remember { mutableStateOf("2") }
    var weeksText by remember { mutableStateOf("1-16周") }
    var error by remember { mutableStateOf<String?>(null) }

    // dayOfWeek 约定与金智解析器一致：星期一=1 … 星期日=7。
    val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    fun buildCourse(): Course? {
        val day = dayNames.indexOf(dayText) + 1
        val start = startSectionText.toIntOrNull() ?: return null
        val end = endSectionText.toIntOrNull() ?: return null
        val duration = end - start + 1
        val weeks = WeekPatternParser.parse(weeksText)
        if (name.isBlank() || day !in 1..7 || start !in 1..TimeTableConfig.sectionCount ||
            end !in start..TimeTableConfig.sectionCount || weeks.isEmpty()
        ) {
            return null
        }
        return Course(
            name = name.trim(),
            teacher = teacher.trim(),
            location = location.trim(),
            dayOfWeek = day,
            startSection = start,
            duration = duration,
            weeks = weeks,
            source = CourseSource.MANUAL,
            color = CourseRepository.autoColor(name.trim()),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dayText, { dayText = it }, label = { Text("星期（星期一~星期日）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startSectionText, { startSectionText = it }, label = { Text("开始节次") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(endSectionText, { endSectionText = it }, label = { Text("结束节次") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(weeksText, { weeksText = it }, label = { Text("周数（如 1-16周 或 1-3单周,6,8-9）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val course = buildCourse()
                if (course == null) {
                    error = "请检查输入：名称必填、节次 1-${TimeTableConfig.sectionCount}、周数格式合法"
                } else {
                    onSave(course)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


