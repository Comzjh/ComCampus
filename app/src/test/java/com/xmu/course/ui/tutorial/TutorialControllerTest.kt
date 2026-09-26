package com.xmu.course.ui.tutorial

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Anchor bookkeeping for [TutorialController]: a scrollable page reports an empty rect for every
 * anchor it has culled, and several nodes can share one target key (a control in each list row).
 */
class TutorialControllerTest {
    private val key = TutorialTargetKey.TODO_CHECKBOX
    private val visible = Rect(84f, 630f, 147f, 693f)
    private val lower = Rect(84f, 840f, 147f, 903f)
    private val culled = Rect(0f, 0f, 0f, 0f)

    @Test
    fun culledSiblingKeepsVisibleAnchorRect() {
        val controller = TutorialController()
        val onScreen = TutorialAnchor(key)
        val offScreen = TutorialAnchor(key)
        controller.onAnchorPositioned(onScreen, visible)
        controller.onAnchorPositioned(offScreen, culled)

        assertEquals(visible, controller.targetRect(key))
    }

    @Test
    fun topmostVisibleAnchorWins() {
        val controller = TutorialController()
        controller.onAnchorPositioned(TutorialAnchor(key), lower)
        controller.onAnchorPositioned(TutorialAnchor(key), visible)

        assertEquals(visible, controller.targetRect(key))
    }

    @Test
    fun targetRectIsEmptyWhenEveryAnchorIsCulled() {
        val controller = TutorialController()
        val anchor = TutorialAnchor(key)
        controller.onAnchorPositioned(anchor, visible)
        controller.onAnchorPositioned(anchor, culled)

        assertNull(controller.targetRect(key))
    }

    @Test
    fun disposedAnchorStopsReporting() {
        val controller = TutorialController()
        val first = TutorialAnchor(key)
        val second = TutorialAnchor(key)
        controller.onAnchorPositioned(first, visible)
        controller.onAnchorPositioned(second, lower)
        controller.onAnchorDisposed(second)

        assertEquals(visible, controller.targetRect(key))

        controller.onAnchorDisposed(first)
        assertNull(controller.targetRect(key))
    }

    @Test
    fun otherKeysAreNotAffected() {
        val controller = TutorialController()
        controller.onAnchorPositioned(TutorialAnchor(key), visible)

        assertNull(controller.targetRect(TutorialTargetKey.TODO_ADD))
    }
}
