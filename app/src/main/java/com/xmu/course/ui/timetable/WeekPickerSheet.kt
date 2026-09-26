package com.xmu.course.ui.timetable


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.background.TimetableBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 周选择器 BottomSheet：动态 1..totalWeeks 四列网格。
 * 实际周描边提示，查看周高亮填充；"回到本周"一键复位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeekPickerSheet(
    totalWeeks: Int,
    viewWeek: Int,
    actualWeek: Int,
    onSelect: (Int) -> Unit,
    onBackToCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WeekPickerSheetContent(
            totalWeeks = totalWeeks,
            viewWeek = viewWeek,
            actualWeek = actualWeek,
            onSelect = onSelect,
            onBackToCurrent = onBackToCurrent,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun WeekPickerSheetContent(
    totalWeeks: Int,
    viewWeek: Int,
    actualWeek: Int,
    onSelect: (Int) -> Unit,
    onBackToCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("选择周", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                onBackToCurrent()
                onDismiss()
            }) { Text("回到本周") }
        }
        Text(
            "实心表示正在查看；描边表示本周",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("week_picker_legend"),
        )
        val rows = (totalWeeks + 3) / 4
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height((rows * 56).dp)
                .selectableGroup()
                .testTag("week_picker_grid"),
        ) {
            items((1..totalWeeks).toList()) { week ->
                val selected = week == viewWeek
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(shape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .then(
                            if (week == actualWeek && !selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(week)
                                onDismiss()
                            },
                        )
                        .semantics {
                            contentDescription = buildString {
                                append("第${week}周")
                                if (selected) append("，当前选中周")
                                if (week == actualWeek) append("，本周")
                            }
                        }
                        .testTag("week_picker_week_$week"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$week",
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}
