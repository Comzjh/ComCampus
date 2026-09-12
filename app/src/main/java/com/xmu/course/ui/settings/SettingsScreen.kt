package com.xmu.course.ui.settings

import com.xmu.course.data.DisplaySettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 设置页：当前学期设置（开学日期）+ 删除课表 + 关于。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenTimetableManager: () -> Unit = {},
    onOpenCourseManager: () -> Unit = {},
    onOpenTimetableSettings: () -> Unit = {},
    onOpenBackgroundPicker: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // 网格线为全局设置；周显示/课程信息显隐已改为每课表独立的 TimetableConfig。
    val showGrid by DisplaySettings.showGrid.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ---- 当前学期 ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前学期", style = MaterialTheme.typography.titleMedium)
                    val semester = state.semester
                    if (semester == null) {
                        Text(
                            "尚无学期数据，请先导入课表",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Text(
                            semester.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = "开学日期：" + (semester.startDate ?: "未设置"),
                            color = if (semester.startDate == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(if (semester.startDate == null) "设置开学日期" else "修改开学日期")
                        }
                    }
                }
            }

            // ---- 课表显示 ----
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("课表显示", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "网格线、课程信息与文字对齐已移动到“课表设置”。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenBackgroundPicker,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("课表背景")
                    }
                    Button(
                        onClick = onOpenTimetableSettings,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("当前课表显示设置")
                    }
                    Text(
                        "教师/地点/备注/时间显隐、周六周日、非本周课程，均已移入每课表独立设置。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // ---- 删除课表 ----
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "删除当前学期已导入/手动添加的全部课程。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = onOpenTimetableManager,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("课表管理")
                    }
                    Button(
                        onClick = onOpenCourseManager,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("课程管理")
                    }
                    Button(
                        onClick = { showDeleteConfirm = true },
                        enabled = state.semester != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("删除当前学期课表")
                    }
                }
            }

            // ---- 关于 ----
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("关于 XMU Course", style = MaterialTheme.typography.titleMedium)
                    Text("版本 v0.6.2", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    Text("厦大学生本地课表 · Made for XMU students", modifier = Modifier.padding(top = 8.dp))
                    Text("MIT License", modifier = Modifier.padding(top = 4.dp))
                    Button(
                        onClick = onOpenSupport,
                        modifier = Modifier.padding(top = 10.dp),
                    ) { Text("支持开发与赞助") }
                    Text(
                        "本地优先 · 无服务器 · 不收集任何数据",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    // ---- 开学日期选择 ----
    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.saveStartDate(date.toString())
                    }
                    showDatePicker = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ---- 删除二次确认 ----
    if (showDeleteConfirm) {
        var deleteSemesterToo by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("将删除当前学期的全部课程，此操作不可撤销。")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Checkbox(checked = deleteSemesterToo, onCheckedChange = { deleteSemesterToo = it })
                        Text("同时删除学期记录")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTimetable(deleteSemesterToo)
                    showDeleteConfirm = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
