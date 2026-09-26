package com.xmu.course.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Provide the page controller inside a TutorialHost. */
val LocalTutorialController = compositionLocalOf<TutorialController?> { null }

/** Provide the app preference store around the nav host. */
val LocalTutorialPreference = compositionLocalOf<TutorialPreferenceStore?> { null }

internal data class TutorialToolbarLauncherState(
    val isCollapsed: Boolean,
    val showHint: Boolean,
    val onExpand: () -> Unit,
    val onCollapse: () -> Unit,
    val onStart: () -> Unit,
    val onDismissHint: () -> Unit,
)

/** Read by a page's top toolbar to place its tutorial entry beside the page actions. */
internal val LocalTutorialToolbarLauncher =
    compositionLocalOf<TutorialToolbarLauncherState?> { null }

/**
 * E2：教程 → 完整功能指南的跳转。由 NavHost 外层提供，参数是 TutorialRegistry 的 id。
 *
 * 用 CompositionLocal 而不是逐页透传回调：教程是横切能力，20 多个页面各自加一个
 * `onOpenGuide` 参数只会让装配层长出与业务无关的噪音。
 */
val LocalTutorialGuideLauncher = compositionLocalOf<((String) -> Unit)?> { null }

/**
 * Wraps a page root: provides the page tutorial toolbar action and active tutorial overlay.
 * [tutorialId] must match a TutorialRegistry id; unknown ids render without tutorial UI.
 */
@Composable
fun TutorialHost(
    tutorialId: String?,
    modifier: Modifier = Modifier,
    controller: TutorialController = remember { TutorialController() },
    preferenceStore: TutorialPreferenceStore? = LocalTutorialPreference.current,
    content: @Composable BoxScope.() -> Unit,
) {
    var hostRect by remember { mutableStateOf(Rect.Zero) }
    val definition = tutorialId?.let { TutorialRegistry.get(it) }
    val guideLauncher = LocalTutorialGuideLauncher.current
    var launcherCollapsed by remember {
        mutableStateOf(preferenceStore?.isLauncherCollapsed() ?: true)
    }
    var hintDismissed by remember(definition?.id) { mutableStateOf(false) }
    val toolbarLauncher = if (definition != null && controller.activeId != definition.id) {
        TutorialToolbarLauncherState(
            isCollapsed = launcherCollapsed,
            showHint = preferenceStore != null &&
                !preferenceStore.hasCompleted(definition.seenKey) && !hintDismissed,
            onExpand = {
                launcherCollapsed = false
                preferenceStore?.setLauncherCollapsed(false)
            },
            onCollapse = {
                launcherCollapsed = true
                hintDismissed = true
                preferenceStore?.setLauncherCollapsed(true)
            },
            onStart = { controller.start(definition.id) },
            onDismissHint = { hintDismissed = true },
        )
    } else {
        null
    }

    Box(
        modifier = modifier.onGloballyPositioned { hostRect = it.boundsInRoot() },
    ) {
        CompositionLocalProvider(
            LocalTutorialController provides controller,
            LocalTutorialToolbarLauncher provides toolbarLauncher,
        ) {
            content()
        }
        if (definition != null && controller.activeId == definition.id) {
            val step = controller.currentStep
            if (step != null) {
                // D5：无目标引导步没有锚点，不去查找也不画红框。
                val rawRect = step.targetKey?.let { controller.targetRect(it) }
                val overlayRect = if (hostRect == Rect.Zero) {
                    rawRect
                } else {
                    rawRect?.translate(-hostRect.topLeft)
                }
                TutorialOverlay(
                    step = step,
                    stepIndex = controller.stepIndex,
                    totalSteps = definition.steps.size,
                    targetRect = overlayRect,
                    isLastStep = controller.isLastStep,
                    onNext = controller::next,
                    onPrevious = controller::previous,
                    onDismiss = {
                        preferenceStore?.markCompleted(definition.seenKey)
                        controller.close()
                    },
                    onFinish = {
                        preferenceStore?.markCompleted(definition.seenKey)
                        controller.close()
                    },
                    onOpenGuide = guideLauncher?.let { launcher ->
                        { launcher(definition.id) }
                    },
                )
            }
        }
    }
}

/**
 * Marks a composable as a tutorial anchor.
 *
 * Contract (TUT-009): without a [LocalTutorialController] — plain previews, screenshot
 * harnesses, or any page rendered outside a [TutorialHost] — this modifier is a strict
 * no-op. It contributes no measurement, no layout, no drawing and no semantics, so the
 * node keeps exactly the size produced by the preceding modifier chain, and it never
 * requires a controller to be present. Callers therefore do not need to branch on
 * "tutorial available" before applying an anchor.
 */
fun Modifier.tutorialTarget(key: String): Modifier = composed {
    val controller = LocalTutorialController.current
    if (controller == null) {
        Modifier
    } else {
        val anchor = remember(key) { TutorialAnchor(key) }
        DisposableEffect(anchor) {
            onDispose { controller.onAnchorDisposed(anchor) }
        }
        this.onGloballyPositioned { coordinates ->
            controller.onAnchorPositioned(anchor, coordinates.boundsInRoot())
        }
    }
}

/**
 * Scrolls [scrollState] so the active tutorial step's anchor sits just below [contentTop],
 * keeping the red frame visible for targets below the fold. Call once inside a vertically
 * scrollable page that hosts a tutorial with the matching [tutorialId], after the page's
 * `Scaffold` inner padding is known.
 *
 * A scrollable page only measures the rows that intersect the viewport, so an anchor that is
 * currently off screen reports no rect at all. The helper therefore scans the page in both
 * directions until the anchor is measured, then aligns it.
 */
@Composable
fun TutorialAutoScroll(
    scrollState: ScrollState,
    tutorialId: String,
    contentTop: Dp = 48.dp,
) {
    val density = LocalDensity.current
    val controller = LocalTutorialController.current
    LaunchedEffect(controller?.activeId, controller?.stepIndex) {
        if (controller?.activeId != tutorialId) return@LaunchedEffect
        val key = controller.currentStep?.targetKey ?: return@LaunchedEffect
        val margin = with(density) { (contentTop + 20.dp).toPx() }
        val stride = with(density) { 320.dp.toPx() }.toInt().coerceAtLeast(1)

        suspend fun scan(from: Int): Rect? {
            var offset = from
            while (offset < scrollState.maxValue) {
                offset = (offset + stride).coerceAtMost(scrollState.maxValue)
                scrollState.animateScrollTo(offset)
                delay(60)
                controller.targetRect(key)?.let { return it }
            }
            return null
        }

        suspend fun reveal(): Rect? {
            controller.targetRect(key)?.let { return it }
            val origin = scrollState.value
            scan(origin)?.let { return it }
            scrollState.animateScrollTo(0)
            delay(60)
            controller.targetRect(key)?.let { return it }
            val found = scan(0)
            if (found == null && scrollState.value != origin) scrollState.animateScrollTo(origin)
            return found
        }

        var rect = reveal() ?: return@LaunchedEffect
        // An anchor left above the content area by an earlier step reports a clipped rect,
        // and aligning from that rect keeps the highlight a sliver. Re-measure it from the
        // top of the page, where every child of the column is laid out at its real offset.
        if (rect.top < margin) {
            scrollState.animateScrollTo(0)
            delay(80)
            rect = controller.targetRect(key) ?: rect
        }
        val target = (rect.top + scrollState.value - margin)
            .toInt()
            .coerceIn(0, scrollState.maxValue)
        if (target != scrollState.value) {
            scrollState.animateScrollTo(target)
            delay(80)
            controller.targetRect(key)?.let { rect = it }
        }
    }
}
