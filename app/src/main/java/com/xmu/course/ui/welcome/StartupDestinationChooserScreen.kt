package com.xmu.course.ui.welcome

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmu.course.contracts.presentation.StartupDestination
import com.xmu.course.ui.theme.AppShapes
import com.xmu.course.ui.theme.AppSpacing

/**
 * 冷启动默认页选择：一次性、简洁，不做大型 onboarding。
 *
 * 用户选择「首页」或「课表」后持久化，之后不再询问，仅可在设置中修改。
 */
@Composable
fun StartupDestinationChooserScreen(
    onChoose: (StartupDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<StartupDestination?>(null) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.Xl),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "打开应用时，你想先看到什么？",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(AppSpacing.Xs))
            Text(
                "选好后即可开始使用，之后可在设置中更改。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppSpacing.Xl))
            StartupOption(
                destination = StartupDestination.HOME,
                title = "首页",
                description = "查看今日课程、待办和近期概览",
                icon = Icons.Outlined.Today,
                selected = selected == StartupDestination.HOME,
                onSelect = { selected = StartupDestination.HOME },
                tag = "startup_choice_home",
            )
            Spacer(Modifier.height(AppSpacing.Md))
            StartupOption(
                destination = StartupDestination.TIMETABLE,
                title = "课表",
                description = "直接进入本周课程表",
                icon = Icons.Outlined.CalendarMonth,
                selected = selected == StartupDestination.TIMETABLE,
                onSelect = { selected = StartupDestination.TIMETABLE },
                tag = "startup_choice_timetable",
            )
            Spacer(Modifier.height(AppSpacing.Xl))
            Button(
                onClick = { selected?.let(onChoose) },
                enabled = selected != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("startup_choice_continue"),
            ) {
                Text("继续")
            }
        }
    }
}

@Composable
private fun StartupOption(
    destination: StartupDestination,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    tag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppSpacing.Xxl),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
