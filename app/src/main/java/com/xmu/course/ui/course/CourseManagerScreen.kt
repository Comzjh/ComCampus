package com.xmu.course.ui.course

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.domain.Course
import com.xmu.course.ui.timetable.AddCourseDialog

/** 编辑弹层颜色候选（与自动配色一致）。 */
private val COLOR_PALETTE = listOf(
    "#FAAC8F", "#FDCF93", "#93D36E", "#7FD4E0",
    "#A79FE1", "#F49BC1", "#8FBCFA", "#E8C877",
)

/**
 * 课程管理页：当前课表全部课程的两列卡片列表。
 * 点击编辑（CourseEditSheet）、长按删除确认、FAB 手动添加。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CourseManagerScreen(
    onBack: () -> Unit,
    onOpenSkipCourses: () -> Unit = {},
    viewModel: CourseManagerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var editTarget by remember { mutableStateOf<Course?>(null) }
    var deleteTarget by remember { mutableStateOf<Course?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("课程管理")
                        state.timetable?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenSkipCourses,
                        enabled = state.courses.isNotEmpty(),
                    ) { Text("翘课设置") }
                    TextButton(
                        onClick = { showClearConfirm = true },
                        enabled = state.courses.isNotEmpty(),
                    ) { Text("清空") }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加课程")
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { }

            state.courses.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无课程\n点击右下角 + 手动添加，或去导入页保存课表",
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = innerPadding,
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.courses, key = { it.id }) { course ->
                    ManagedCourseCard(
                        course = course,
                        onClick = { editTarget = course },
                        onLongClick = { deleteTarget = course },
                    )
                }
            }
        }
    }

    // ---- 手动添加 ----
    if (showAddDialog) {
        AddCourseDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.addCourse(it)
                showAddDialog = false
            },
        )
    }

    // ---- 编辑 ----
    editTarget?.let { target ->
        CourseEditSheet(
            course = target,
            onDismiss = { editTarget = null },
            onSave = { name, teacher, location, note, color ->
                viewModel.updateCourseName(target.id, name)
                viewModel.updateCourseTeacher(target.id, teacher)
                viewModel.updateCourseLocation(target.id, location)
                viewModel.updateCourseNote(target.id, note)
                viewModel.updateCourseColor(target.id, color)
                editTarget = null
            },
        )
    }

    // ---- 清空确认 ----
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部课程？") },
            text = { Text("将删除当前课表的全部课程，此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllCourses()
                        showClearConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }

    // ---- 删除确认 ----
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除课程？") },
            text = { Text("课程：${target.name}\n此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCourse(target.id)
                        deleteTarget = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 课程管理卡片：颜色点 + 名称/教师/地点/周次/备注。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManagedCourseCard(
    course: Course,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            course.color.takeIf { it.isNotBlank() }
                                ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                ?: MaterialTheme.colorScheme.primary,
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    course.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (course.teacher.isNotBlank()) {
                Text(
                    course.teacher,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (course.location.isNotBlank()) {
                Text(
                    course.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                weeksLabel(course.weeks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (course.note.isNotBlank()) {
                Text(
                    "备注：${course.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 周次集合 → "1-16周" / "1-3,5,8-9周" 展示。 */
internal fun weeksLabel(weeks: Set<Int>): String {
    if (weeks.isEmpty()) return "未设置周次"
    val sorted = weeks.sorted()
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var prev = start
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            ranges += start..prev
            start = w
            prev = w
        }
    }
    ranges += start..prev
    return ranges.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" } + "周"
}

/**
 * 课程编辑弹层：名称 / 教师 / 地点 / 备注 / 颜色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseEditSheet(
    course: Course,
    onDismiss: () -> Unit,
    onSave: (name: String, teacher: String, location: String, note: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf(course.name) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var location by remember { mutableStateOf(course.location) }
    var note by remember { mutableStateOf(course.note) }
    var color by remember { mutableStateOf(course.color.ifBlank { COLOR_PALETTE.first() }) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("编辑课程", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text("地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COLOR_PALETTE.forEach { candidate ->
                    val selected = candidate == color
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(runCatching { Color(android.graphics.Color.parseColor(candidate)) }.getOrDefault(Color.Gray))
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                            .clickable { color = candidate },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = { onSave(name, teacher, location, note, color) },
                    enabled = name.isNotBlank(),
                ) { Text("保存") }
            }
        }
    }
}
