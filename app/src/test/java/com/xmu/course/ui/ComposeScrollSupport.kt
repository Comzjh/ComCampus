package com.xmu.course.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp

/**
 * Robolectric 默认视口很小，LazyColumn 只会组合视口内的行：
 * 页面下方的卡片尚未进入语义树，performScrollTo() 因此找不到节点。
 *
 * 先用手势把列表往下推，直到目标真的被组合，再交给 performScrollTo() 精确对齐。
 * 断言依旧要求「滚动后用户看得见」，不靠放宽匹配假通过。
 */
internal fun ComposeTestRule.scrollUntilComposed(
    matcher: SemanticsMatcher,
    maxSwipes: Int = 8,
): SemanticsNodeInteraction {
    repeat(maxSwipes) {
        if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) {
            return onNode(matcher).performScrollTo()
        }
        onRoot().performTouchInput { swipeUp() }
        waitForIdle()
    }
    // 仍未组合：把失败留给 performScrollTo()，报出原始定位错误而不是吞掉断言
    return onNode(matcher).performScrollTo()
}
