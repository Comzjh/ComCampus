package com.xmu.course.ui.manager

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.data.TimetableWithCount

/**
 * 课表管理页：列表 / 点击切换 / 长按重命名删除 / 新建。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimetableManagerScreen(
    onBack: () -> Unit,
    onOpenTimetableSettings: () -> Unit = {},
    viewModel: TimetableManagerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var manageTarget by remember { mutableStateOf<TimetableWithCount?>(null) }
    var renameTarget by remember { mutableStateOf<TimetableWithCount?>(null) }
    var deleteTarget by remember { mutableStateOf<TimetableWithCount?>(null) }

    // 操作结果提示。
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表管理") },
                actions = {
                    IconButton(onClick = onOpenTimetableSettings) {
                        Icon(Icons.Filled.Tune, contentDescription = "显示设置")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "创建新课表")
            }
        },
    ) { innerPadding ->
        if (state.timetables.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有课表\n点击右下角 + 创建", textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.padding(innerPadding).padding(horizontal = 12.dp)) {
                if (state.timetables.size > 1) {
                    Text(
                        "点击切换当前课表，长按管理",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.timetables, key = { it.timetable.id }) { item ->
                        TimetableCard(
                            item = item,
                            isCurrent = item.timetable.id == state.currentId,
                            onClick = { viewModel.select(item.timetable.id) },
                            onLongClick = { manageTarget = item },
                        )
                    }
                }
            }
        }
    }

    // ---- 新建 ----
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("创建新课表") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createTimetable(name)
                        showCreateDialog = false
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            },
        )
    }

    // ---- 长按管理（重命名 / 删除）----
    manageTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { manageTarget = null },
            title = { Text(target.timetable.name) },
            text = {
                Text(
                    if (target.isCustom) "自定义课表 · 共 ${target.courseCount} 门课程"
                    else "导入课表 · 共 ${target.courseCount} 门课程",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manageTarget = null
                    renameTarget = target
                }) { Text("重命名") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        manageTarget = null
                        deleteTarget = target
                    }) { Text("删除") }
                    TextButton(onClick = { manageTarget = null }) { Text("关闭") }
                }
            },
        )
    }

    // ---- 重命名 ----
    renameTarget?.let { target ->
        var name by remember(target) { mutableStateOf(target.timetable.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名课表") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameTimetable(target.timetable.id, name)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    // ---- 删除确认 ----
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = {
                Text(
                    if (target.isCustom) {
                        "自定义课表「${target.timetable.name}」及其 ${target.courseCount} 门课程将被删除，此操作不可撤销。"
                    } else {
                        "仅删除课表记录「${target.timetable.name}」，学期与课程数据将保留。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTimetable(target.timetable.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 课表卡片：颜色点 + 名称 + 当前周/开学日期 + 课程数 + 当前标记。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableCard(
    item: TimetableWithCount,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        item.timetable.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                            ?: MaterialTheme.colorScheme.primary,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.timetable.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "第 ${item.timetable.currentWeek} 周 · " +
                        (item.timetable.startDate?.let { "开学 $it" } ?: "开学日期未设置"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${item.courseCount} 门课程",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "当前使用",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
