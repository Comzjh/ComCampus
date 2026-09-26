package com.xmu.course.ui.tutorial

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Compact tutorial entry displayed in the page toolbar while the launcher is collapsed. */
@Composable
fun TutorialLauncherHandle(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onExpand,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "展开教程入口" }
            .testTag("tutorial_launcher_handle"),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
