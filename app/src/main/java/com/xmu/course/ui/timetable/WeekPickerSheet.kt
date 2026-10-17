package com.xmu.course.ui.timetable


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.theme.AppShapes

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = AppShapes.SheetTop,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
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
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("选择周", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                .padding(top = 4.dp, bottom = 16.dp)
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
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                        .then(
                            if (week == actualWeek && !selected) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
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
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
