package com.xmu.course.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialOverlayLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryActionStaysVisibleAt360DpAndLargeFontScaleWithGuideActions() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.3f),
            ) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        TutorialOverlay(
                            step = TutorialStep(
                                targetKey = null,
                                title = "当前页面",
                                message = "本页说明使用合成内容，不触发页面导航。",
                            ),
                            stepIndex = 2,
                            totalSteps = 3,
                            targetRect = null,
                            isLastStep = true,
                            onNext = {},
                            onPrevious = {},
                            onDismiss = {},
                            onFinish = {},
                            onOpenGuide = {},
                        )
                    }
                }
            }
        }

        val maxWidthPx = with(composeRule.density) { 360.dp.toPx() }
        val minActionHeightPx = with(composeRule.density) { 48.dp.toPx() }
        listOf(
            "tutorial_open_guide",
            "tutorial_action_skip",
            "tutorial_action_previous",
            "tutorial_action_primary",
        ).forEach { tag ->
            val action = composeRule.onNodeWithTag(tag)
            action.assertIsDisplayed().assertHasClickAction()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag must stay within the 360dp viewport", bounds.left >= 0f && bounds.right <= maxWidthPx)
            assertTrue("$tag must keep a 48dp touch target", bounds.height >= minActionHeightPx)
            assertTrue("$tag must stay above the bottom edge", bounds.bottom <= with(composeRule.density) { 800.dp.toPx() })
        }
    }
}
