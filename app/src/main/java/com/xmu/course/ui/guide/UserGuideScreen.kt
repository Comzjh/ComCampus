package com.xmu.course.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class GuideSection(
    val title: String,
    val summary: String,
    val steps: List<String>,
    val tip: String,
)

private val GUIDE_SECTIONS = listOf(
    GuideSection("欢迎使用", "XMU Course 是厦大学生的本地课表工具。", listOf(
        "第一次打开时，先阅读引导并进入主界面。",
        "课表、背景和翘课标记都保存在本机，换设备需要重新导入。",
        "底部导航分为课表、导入、使用教程和设置四个区域。",
    ), "建议先完成一次课表导入，再按自己的习惯调整显示。"),
    GuideSection("导入课表", "从厦大金智教务系统读取当前学期课表。", listOf(
        "打开底部“导入”，点击进入金智教务页面。",
        "在应用内网页登录厦大教务系统；登录信息只在 WebView 会话中使用。",
        "回到课表页面并保存，等待解析完成提示。",
        "导入完成后，课程会写入本地 Room 数据库，之后查看课表不依赖网络。",
    ), "重复导入同一学期时，应用会先提示冲突，再由你决定是否覆盖。"),
    GuideSection("查看课表", "用周视图快速确认今天和接下来几周的安排。", listOf(
        "课表页默认定位当前教学周；顶部可以打开周选择器。",
        "单周、双周课程会按课程自身周次显示，切换周次即可核对。",
        "连堂课会保持连续高度；同一时段的冲突课程会自动分栏。",
        "点击课程卡可以查看教师、地点、时间、备注并编辑颜色。",
    ), "如果开学日期不准确，请到设置里的当前学期修改，当前周计算会随之更新。"),
    GuideSection("个性化设置", "把课表调整成适合自己屏幕和阅读习惯的样子。", listOf(
        "在设置中打开“课表背景”，选择内置厦大背景或自定义图片。",
        "用模糊、遮罩和裁剪位置控制背景对文字的干扰。",
        "在当前课表显示设置中调整课程高度、圆角、字号、网格线和文字对齐。",
        "教师、地点、备注等信息可以按课表单独开关。",
    ), "小屏幕建议先降低字号和课程高度，再决定是否显示备注。"),
    GuideSection("翘课模式", "保留课程位置，只把暂时不参加的课程变淡。", listOf(
        "进入设置 → 课程管理 → 翘课设置。",
        "勾选一门或多门课程，底部按钮会显示当前选择数量。",
        "点击保存后，课程仍会出现在原来的周次、位置和冲突分栏中。",
        "也可以直接点开课程详情，使用“标记翘课”或“取消翘课”。",
    ), "翘课状态只改变视觉显示，不会删除课程，也不会影响导入和布局。"),
    GuideSection("桌面 Widget", "不用打开应用，也能看到今天的安排和下一节课。", listOf(
        "在系统桌面长按空白处，选择添加小组件。",
        "选择“今日课程”（4×2）查看当天课程列表，或选择“下一节课”（2×2）。",
        "组件自动读取当前课表，不提供课表选择，避免桌面出现多套状态。",
        "课程变化、翘课标记、当前周变化和应用启动都会触发刷新；下一节课每分钟更新。",
        "点击今日课程打开课表，点击下一节课打开对应课程详情。",
    ), "组件显示时间、课程名称和地点，空间不足时会优先隐藏教师与备注。"),
    GuideSection("数据安全", "本地优先，保持课表数据的边界清晰。", listOf(
        "课程和显示设置保存在本机数据库与本地偏好设置中。",
        "应用不会保存教务密码，也不会把登录会话写入课程数据。",
        "删除学期或清空课程前会再次确认；删除后无法从应用内恢复。",
        "卸载应用会同时清理本机数据库和设置。",
    ), "使用公共设备时，请在离开前退出教务 WebView，并按需清理本机课表。"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用教程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("XMU Course", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "从导入课表到桌面提醒，按章节找到你需要的操作。",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            items(GUIDE_SECTIONS.size) { index ->
                val section = GUIDE_SECTIONS[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Text(
                            section.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        section.steps.forEachIndexed { stepIndex, step ->
                            Text(
                                "${stepIndex + 1}. $step",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text(
                                "提示：${section.tip}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
