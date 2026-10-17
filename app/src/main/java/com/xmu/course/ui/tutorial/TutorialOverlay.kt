package com.xmu.course.ui.tutorial

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.xmu.course.ui.components.AppButton as Button
import androidx.compose.material3.Icon
import com.xmu.course.ui.components.AppIconButton as IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.xmu.course.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Dimmed overlay with a brand-blue rounded stroke around [targetRect].
 * Clicks outside the highlight are swallowed (tutorial is not dismissed).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutorialOverlay(
    step: TutorialStep,
    stepIndex: Int,
    totalSteps: Int,
    targetRect: Rect?,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    /** E2：跳到本章功能指南；没有对应指南时传 null，不显示入口。 */
    onOpenGuide: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
    ) {
        val density = LocalDensity.current
        // TUT-007: a target can be taller than the space left under it, and fixed
        // bottom placement then hides its lower edge. Pick the side with the larger
        // real gap and cap the card at that gap. The floor is the height needed to
        // show heading + two wrapped copy lines + the controls, because the controls
        // must stay reachable at 1.3x font: when the gap is smaller than that, the
        // card overlaps the highlight instead of swallowing its own buttons.
        val overlayHeightPx = with(density) { maxHeight.toPx() }
        val closeReservedPx = with(density) { 88.dp.toPx() }
        val minCardPx = with(density) { 216.dp.toPx() }
        val freeBelowPx = targetRect?.let { overlayHeightPx - it.bottom } ?: overlayHeightPx
        val freeAbovePx = targetRect?.let { it.top - closeReservedPx } ?: 0f
        val cardAtTop = targetRect != null && freeAbovePx > freeBelowPx
        val cardMaxHeight = if (targetRect == null) null else with(density) {
            maxOf(if (cardAtTop) freeAbovePx else freeBelowPx, minCardPx).toDp()
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.45f))
        }
        // Keep the optional animation in its own node. Removing an anchor must not
        // replace the composition group that owns the current copy and controls.
        if (targetRect != null) {
            TutorialTargetHighlight(targetRect, Modifier.matchParentSize())
        }
        IconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.24f),
                contentColor = Color.White,
            ),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "关闭教程")
        }
        Surface(
            modifier = Modifier
                .align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (cardMaxHeight == null) Modifier else Modifier.heightIn(max = cardMaxHeight))
                .padding(16.dp)
                .padding(top = if (cardAtTop) 88.dp else 0.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Crossfade(
                    targetState = "第 ${stepIndex + 1}/$totalSteps 步 · ${step.title}",
                    animationSpec = tween(160),
                    label = "tutorialHeading",
                ) { heading ->
                    Text(text = heading, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Only the copy scrolls; the step heading and the controls stay pinned.
                val message = when {
                    step.targetKey == null -> step.message
                    targetRect == null -> "当前页面没有可用教程内容"
                    else -> step.message
                }
                Crossfade(
                    targetState = message,
                    animationSpec = tween(160),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    label = "tutorialCopy",
                ) { copy ->
                    Text(text = copy, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("tutorial_secondary_actions"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (onOpenGuide != null) {
                        TextButton(
                            onClick = onOpenGuide,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("tutorial_open_guide"),
                        ) {
                            Text(text = "详细教程")
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tutorial_action_skip"),
                    ) {
                        Text(text = "跳过")
                    }
                    if (stepIndex > 0) {
                        TextButton(
                            onClick = onPrevious,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("tutorial_action_previous"),
                        ) {
                            Text(text = "上一步")
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = if (isLastStep) onFinish else onNext,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tutorial_action_primary"),
                    ) {
                        Text(text = if (isLastStep) "完成" else "下一步")
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialTargetHighlight(targetRect: Rect, modifier: Modifier) {
    val density = LocalDensity.current
    val strokePx = with(density) { 2.5.dp.toPx() }
    val padPx = with(density) { 3.dp.toPx() }
    val cornerPx = with(density) { 12.dp.toPx() }
    // A newly available anchor starts at its actual position, never at (0, 0).
    val highlightRect by animateRectAsState(
        targetValue = targetRect,
        animationSpec = tween(180),
        label = "tutorialHighlight",
    )
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = Color(0xFF66CCFF),
            topLeft = Offset(highlightRect.left - padPx, highlightRect.top - padPx),
            size = Size(highlightRect.width + padPx * 2, highlightRect.height + padPx * 2),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = strokePx),
        )
    }
}
