package com.xmu.course.ui.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** One registered anchor. Several nodes can share a [key], for example a row control in a list. */
class TutorialAnchor(val key: String)

/**
 * Holds the active tutorial state and target bounds for the current page.
 * Anchor rects are plain [Rect] values in root coordinates, captured from
 * Modifier.tutorialTarget positioning callbacks; no layout coordinates retained.
 */
class TutorialController {
    var activeId by mutableStateOf<String?>(null)
        private set

    var stepIndex by mutableStateOf(0)
        private set

    /**
     * Rect per anchor node, not per key: a scrollable page reports an empty rect for every
     * anchor it has culled from the viewport, and one such report must not erase the rect of
     * the row that is currently on screen.
     */
    private val anchorRects = mutableStateMapOf<TutorialAnchor, Rect>()

    val activeDefinition: TutorialDefinition?
        get() = activeId?.let { TutorialRegistry.get(it) }

    val currentStep: TutorialStep?
        get() = activeDefinition?.steps?.getOrNull(stepIndex)

    val isLastStep: Boolean
        get() {
            val definition = activeDefinition ?: return false
            return stepIndex >= definition.steps.size - 1
        }

    /** The topmost anchor rect currently visible for [key], or null when none is on screen. */
    fun targetRect(key: String): Rect? = anchorRects
        .filter { (anchor, rect) -> anchor.key == key && rect.width > 0f && rect.height > 0f }
        .minByOrNull { (_, rect) -> rect.top }
        ?.value

    fun onAnchorPositioned(anchor: TutorialAnchor, rect: Rect) {
        anchorRects[anchor] = rect
    }

    fun onAnchorDisposed(anchor: TutorialAnchor) {
        anchorRects.remove(anchor)
    }

    fun start(id: String) {
        val definition = TutorialRegistry.get(id) ?: return
        activeId = definition.id
        stepIndex = 0
    }

    fun next() {
        val definition = activeDefinition ?: return
        if (stepIndex < definition.steps.size - 1) {
            stepIndex += 1
        }
    }

    fun previous() {
        if (stepIndex > 0) {
            stepIndex -= 1
        }
    }

    fun close() {
        activeId = null
        stepIndex = 0
    }
}
