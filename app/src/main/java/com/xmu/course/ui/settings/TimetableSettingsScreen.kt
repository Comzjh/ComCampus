package com.xmu.course.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import com.xmu.course.domain.TimetableConfig

private val horizontalOptions = listOf(
    TextHorizontalAlignment.START to "靠左",
    TextHorizontalAlignment.CENTER to "居中",
    TextHorizontalAlignment.END to "靠右",
)

private val verticalOptions = listOf(
    TextVerticalAlignment.TOP to "顶部",
    TextVerticalAlignment.CENTER to "居中",
    TextVerticalAlignment.BOTTOM to "底部",
)

/**
 * 课表显示设置页：所有影响课表显示的设置集中在这里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSettingsScreen(
    onBack: () -> Unit,
    viewModel: TimetableSettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("课表设置")
                        state.timetableName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        val config = state.config
        if (config == null) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("当前没有课表\n请先创建或导入课表", textAlign = TextAlign.Center)
            }
        } else {
            Column(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                // ---- 显示设置 ----
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("显示设置", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        ConfigSwitch("显示网格线", state.showGrid) { value ->
                            viewModel.setShowGrid(value)
                        }
                        ConfigSwitch("显示周六", config.showSaturday) { value ->
                            viewModel.update { it.copy(showSaturday = value) }
                        }
                        ConfigSwitch("显示周日", config.showSunday) { value ->
                            viewModel.update { it.copy(showSunday = value) }
                        }
                        ConfigSwitch("显示非本周课程", config.showNonCurrentWeek) { value ->
                            viewModel.update { it.copy(showNonCurrentWeek = value) }
                        }
                    }
                }

                // ---- 信息显示 ----
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("信息显示", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        ConfigSwitch("显示教师", config.showTeacher) { value ->
                            viewModel.update { it.copy(showTeacher = value) }
                        }
                        ConfigSwitch("显示地点", config.showLocation) { value ->
                            viewModel.update { it.copy(showLocation = value) }
                        }
                        ConfigSwitch("显示完整时间轴", config.showFullTimeAxis) { value ->
                            viewModel.update { it.copy(showFullTimeAxis = value) }
                        }
                        ConfigSwitch("显示备注", config.showNote) { value ->
                            viewModel.update { it.copy(showNote = value) }
                        }
                    }
                }

                // ---- 文字设置 ----
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("文字设置", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        ConfigSlider(
                            "字体大小",
                            { "${it.toInt()}sp" },
                            config.textSize.toFloat(), 8f, 18f,
                        ) { viewModel.update { c -> c.copy(textSize = it.toInt()) } }

                        Text("水平对齐", Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            horizontalOptions.forEach { (value, label) ->
                                FilterChip(
                                    selected = config.textHorizontalAlignment == value,
                                    onClick = { viewModel.update { it.copy(textHorizontalAlignment = value) } },
                                    label = { Text(label) },
                                )
                            }
                        }

                        Text("垂直对齐", Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            verticalOptions.forEach { (value, label) ->
                                FilterChip(
                                    selected = config.textVerticalAlignment == value,
                                    onClick = { viewModel.update { it.copy(textVerticalAlignment = value) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }

                // ---- 外观 ----
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("外观", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        ConfigSlider(
                            "格子高度",
                            { "${it.toInt()}dp" },
                            config.courseHeight.toFloat(), 44f, 80f,
                        ) { viewModel.update { c -> c.copy(courseHeight = it.toInt()) } }
                        ConfigSlider(
                            "圆角",
                            { "${it.toInt()}dp" },
                            config.cornerRadius.toFloat(), 0f, 16f,
                        ) { viewModel.update { c -> c.copy(cornerRadius = it.toInt()) } }
                        ConfigSwitch("紧凑顶部栏", config.headerCompactMode) { value ->
                            viewModel.update { it.copy(headerCompactMode = value) }
                        }
                        ConfigSlider(
                            "卡片透明度",
                            { "${(it * 100).toInt()}%" },
                            config.courseAlpha, 0.3f, 1f,
                        ) { viewModel.update { c -> c.copy(courseAlpha = it) } }
                    }
                }

                Text(
                    "除网格线为全局开关外，以上课表设置仅对当前课表生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** 外观滑块行：本地状态实时拖动，松手后提交到 Room。 */
@Composable
private fun ConfigSlider(
    title: String,
    valueText: (Float) -> String,
    value: Float,
    min: Float,
    max: Float,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Text(valueText(local), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = local.coerceIn(min, max),
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = min..max,
        )
    }
}

/** 设置行：左标题 + 右 Switch。 */
@Composable
private fun ConfigSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
