package com.xmu.course.ui.timetable


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
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
import com.xmu.course.ui.tutorial.TutorialTargetKey
import com.xmu.course.ui.tutorial.TutorialToolbarAction
import com.xmu.course.ui.tutorial.tutorialTarget
import com.xmu.course.ui.background.TimetableBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 12.dp, end = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(enabled = weekPickerEnabled, onClick = onTitleClick)
                .tutorialTarget(TutorialTargetKey.TIMETABLE_WEEK_SWITCH)
                .testTag("timetable_header_week"),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (onBackToCurrent != null) {
            TextButton(
                onClick = onBackToCurrent,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                    .testTag("timetable_header_back_to_current"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { Text("回到本周") }
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("timetable_header_settings")
                .tutorialTarget(TutorialTargetKey.TIMETABLE_SETTINGS),
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "课表设置",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onImportClick,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("timetable_header_import"),
        ) {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = "导入课表",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onAddClick,
            enabled = addEnabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("timetable_header_add"),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加课程",
                modifier = Modifier.size(20.dp),
                tint = if (addEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }
        TutorialToolbarAction()
    }
}
