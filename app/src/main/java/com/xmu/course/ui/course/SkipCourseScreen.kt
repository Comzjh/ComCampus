package com.xmu.course.ui.course

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.domain.Course

/**
 * 翘课设置：多选当前课表课程，保存为本地显示状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipCourseScreen(
    onBack: () -> Unit,
    viewModel: CourseManagerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var selected by remember(state.skippedCourseIds) { mutableStateOf(state.skippedCourseIds) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("翘课设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Button(
                    onClick = { viewModel.saveSkippedCourses(selected) },
                    enabled = state.courses.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("保存（已选 ${selected.size} 门）")
                }
            }
        },
    ) { padding ->
        if (state.courses.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无课程")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.courses, key = { it.id }) { course ->
                val checked = course.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (checked) selected - course.id else selected + course.id
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { value ->
                            selected = if (value) selected + course.id else selected - course.id
                        },
                    )
                    SkipCourseLabel(course)
                }
            }
        }
    }
}

@Composable
private fun SkipCourseLabel(course: Course) {
    Column(Modifier.padding(start = 4.dp)) {
        Text(course.name, style = MaterialTheme.typography.titleSmall)
        val detail = listOf(course.teacher, course.location).filter { it.isNotBlank() }
        if (detail.isNotEmpty()) {
            Text(
                detail.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
