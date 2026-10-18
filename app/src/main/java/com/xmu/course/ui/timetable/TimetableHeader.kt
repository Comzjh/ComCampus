package com.xmu.course.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xmu.course.ui.components.AppIconButton as IconButton
import com.xmu.course.ui.theme.action
import com.xmu.course.ui.tutorial.LocalTutorialToolbarLauncher
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.tutorialTarget

/** A single toolbar row; secondary actions never expand into timetable space. */
@Composable
internal fun TimetableCompactHeader(
    title: String,
    subtitle: String,
    onTitleClick: () -> Unit,
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBackToCurrent: (() -> Unit)?,
    weekPickerEnabled: Boolean = true,
    addEnabled: Boolean = true,
    compact: Boolean = true,
) {
    var moreOpen by remember { mutableStateOf(false) }
    val tutorial = LocalTutorialToolbarLauncher.current
    val heading = title.removeSuffix(" ▼").replace("第 ", "第").replace(" 周", "周")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 48.dp else 56.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 12.dp, end = 4.dp)
            .testTag("timetable_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = weekPickerEnabled, onClick = onTitleClick)
                .semantics { contentDescription = "$heading，$subtitle" }
                .tutorialTarget(TutorialTargetKey.TIMETABLE_WEEK_SWITCH)
                .testTag("timetable_header_week"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (weekPickerEnabled) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        if (onBackToCurrent != null) {
            IconButton(onClick = onBackToCurrent, modifier = Modifier.testTag("timetable_header_back_to_current")) {
                Icon(Icons.Filled.Today, contentDescription = "回到本周", modifier = Modifier.size(20.dp))
            }
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.testTag("timetable_header_settings")
                .tutorialTarget(TutorialTargetKey.TIMETABLE_SETTINGS),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "课表设置", modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TimetableAddCourseAction(onClick = onAddClick, enabled = addEnabled)
        Box {
            IconButton(onClick = { moreOpen = true }, modifier = Modifier.testTag("timetable_header_more")) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多课表操作", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 240.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text("导入课表") },
                    leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                    onClick = { moreOpen = false; onImportClick() },
                    modifier = Modifier.testTag("timetable_header_import"),
                )
                if (tutorial != null) {
                    DropdownMenuItem(
                        text = { Text("本页教程") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                        onClick = { moreOpen = false; tutorial.onStart() },
                        modifier = Modifier.testTag("timetable_header_tutorial"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TimetableAddCourseAction(onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            )
            .testTag("timetable_header_add"),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "添加课程",
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.action
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}
