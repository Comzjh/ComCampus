package com.xmu.course.ui.tutorial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Expanded tutorial action rendered inside a page's top toolbar. */
@Composable
fun TutorialLauncherButton(
    onClick: () -> Unit,
    onCollapse: () -> Unit,
    showHint: Boolean,
    onDismissHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val popupOffsetY = with(LocalDensity.current) { 52.dp.roundToPx() }
    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .testTag("tutorial_fab")
                    .sizeIn(minHeight = 48.dp)
                    .semantics { contentDescription = "本页使用提示" },
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("教程")
            }
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.testTag("tutorial_launcher_collapse"),
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "收起教程入口")
            }
        }
        if (showHint) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, popupOffsetY),
                onDismissRequest = onDismissHint,
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onClick)
                        .testTag("tutorial_discovery_hint"),
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 3.dp,
                ) {
                    Text(
                        text = "第一次用？点这里查看教程",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Page-level tutorial entry. It is consumed from each page's existing toolbar/header. */
@Composable
fun TutorialToolbarAction(modifier: Modifier = Modifier) {
    val launcher = LocalTutorialToolbarLauncher.current ?: return
    if (launcher.isCollapsed) {
        TutorialLauncherHandle(
            onExpand = launcher.onExpand,
            modifier = modifier,
        )
    } else {
        TutorialLauncherButton(
            onClick = launcher.onStart,
            onCollapse = launcher.onCollapse,
            showHint = launcher.showHint,
            onDismissHint = launcher.onDismissHint,
            modifier = modifier,
        )
    }
}
