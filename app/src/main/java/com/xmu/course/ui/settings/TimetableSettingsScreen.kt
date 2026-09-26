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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import com.xmu.course.data.AxisTextColor
import com.xmu.course.data.TimetableAxisStyle
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.timetable.AxisColorSwatchRow

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
    val axisStyle by TimetablePrefs.axisStyle.collectAsState()
    val axisContext = LocalContext.current

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
                Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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
                Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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
                Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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

                // ---- 课表文字（Phase 9.1 全局显示偏好；Phase 11 扩展星期栏/日期栏独立字号与颜色） ----
                TimetableAxisSettingsSection(
                    axisStyle = axisStyle,
                    modifier = Modifier.padding(top = 12.dp),
                    onPeriodFontChange = { TimetablePrefs.setAxisPeriodFontSp(axisContext, it) },
                    onTimeFontChange = { TimetablePrefs.setAxisTimeFontSp(axisContext, it) },
                    onDateFontChange = { TimetablePrefs.setAxisDateFontSp(axisContext, it) },
                    onPeriodColorChange = { TimetablePrefs.setAxisPeriodColor(axisContext, it) },
                    onTimeColorChange = { TimetablePrefs.setAxisTimeColor(axisContext, it) },
                    onWeekdayFontChange = { TimetablePrefs.setAxisWeekdayFontSp(axisContext, it) },
                    onWeekdayColorChange = { TimetablePrefs.setAxisWeekdayColor(axisContext, it) },
                    onDateColorChange = { TimetablePrefs.setAxisDateColor(axisContext, it) },
                )

                // ---- 外观 ----
                Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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
                    "除网格线与课表文字为全局设置外，以上课表设置仅对当前课表生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/**
 * 课表文字设置区：节次栏/时间栏/星期栏/日期栏的字号与颜色完全独立（Phase 9.1/9.2 及 Phase 11 扩展）。
 * 保存到 TimetablePrefs（SharedPreferences），全局生效、即时预览。
 */
@Composable
internal fun TimetableAxisSettingsSection(
    axisStyle: TimetableAxisStyle,
    modifier: Modifier = Modifier,
    onPeriodFontChange: (Int) -> Unit,
    onTimeFontChange: (Int) -> Unit,
    onDateFontChange: (Int) -> Unit,
    onPeriodColorChange: (AxisTextColor) -> Unit,
    onTimeColorChange: (AxisTextColor) -> Unit,
    onWeekdayFontChange: (Int) -> Unit,
    onWeekdayColorChange: (AxisTextColor) -> Unit,
    onDateColorChange: (AxisTextColor) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("课表文字", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            AxisFontConfigSlider("节次栏字号", axisStyle.periodFontSp) { onPeriodFontChange(it) }
            Text(
                "节次栏颜色",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            AxisColorSwatchRow(
                selected = axisStyle.periodColor,
                modifier = Modifier.padding(top = 8.dp),
                tagPrefix = "axis_period",
                onSelect = onPeriodColorChange,
            )
            AxisFontConfigSlider("时间栏字号", axisStyle.timeFontSp) { onTimeFontChange(it) }
            Text(
                "时间栏颜色",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            AxisColorSwatchRow(
                selected = axisStyle.timeColor,
                modifier = Modifier.padding(top = 8.dp),
                tagPrefix = "axis_time",
                onSelect = onTimeColorChange,
            )
            AxisFontConfigSlider("星期栏字号", axisStyle.weekdayFontSp) { onWeekdayFontChange(it) }
            Text(
                "星期栏颜色",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            AxisColorSwatchRow(
                selected = axisStyle.weekdayColor,
                modifier = Modifier.padding(top = 8.dp),
                tagPrefix = "axis_weekday",
                onSelect = onWeekdayColorChange,
            )
            AxisFontConfigSlider("日期栏字号", axisStyle.dateFontSp) { onDateFontChange(it) }
            Text(
                "日期栏颜色",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            AxisColorSwatchRow(
                selected = axisStyle.dateColor,
                modifier = Modifier.padding(top = 8.dp),
                tagPrefix = "axis_date",
                onSelect = onDateColorChange,
            )
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
    steps: Int = 0,
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
            steps = steps,
        )
    }
}

/** 课表文字字号行：四栏共用 TimetableAxisStyle.AXIS_FONT_RANGE，相同字号滑块位置一致。 */
@Composable
private fun AxisFontConfigSlider(
    title: String,
    valueSp: Int,
    onCommit: (Int) -> Unit,
) {
    ConfigSlider(
        title = title,
        valueText = { "${it.toInt()}sp" },
        value = valueSp.toFloat(),
        min = TimetableAxisStyle.AXIS_FONT_RANGE.first.toFloat(),
        max = TimetableAxisStyle.AXIS_FONT_RANGE.last.toFloat(),
        steps = TimetableAxisStyle.AXIS_FONT_RANGE.last - TimetableAxisStyle.AXIS_FONT_RANGE.first - 1,
    ) { onCommit(it.toInt()) }
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
