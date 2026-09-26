package com.xmu.course.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal const val DEFAULT_COURSE_COLOR = "#FAAC8F"

private val courseColors = listOf(
    DEFAULT_COURSE_COLOR to "珊瑚橙",
    "#FDCF93" to "浅杏色",
    "#93D36E" to "草绿色",
    "#7FD4E0" to "湖蓝色",
    "#A79FE1" to "淡紫色",
    "#F49BC1" to "粉红色",
    "#8FBCFA" to "天蓝色",
    "#E8C877" to "金黄色",
)

/** Shared, wrapping course color choices with a 48dp radio target and spoken color name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseColorSelector(
    selectedColor: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "course_color_selector",
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup().testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        courseColors.forEach { (hex, label) ->
            val selected = selectedColor.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onColorChange(hex) },
                    )
                    .semantics { contentDescription = "课程颜色：$label" }
                    .testTag("$testTag-$hex"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text("✓", color = Color(0xFF171B21), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
