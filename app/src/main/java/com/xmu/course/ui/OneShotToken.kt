package com.xmu.course.ui

/**
 * 一次性事件令牌：同一个令牌值只允许被认领一次。
 *
 * UI 状态里的完成令牌只在真实成功时 +1，导航层用本类去重，
 * 因此"重复回调、两个入口竞速、页面重进"都不会二次导航，而下一次成功仍然会导航。
 */
class OneShotToken {
    private var handled: Long = 0L

    /** [current] 是尚未被认领过的令牌时返回 true，并立即标记为已认领。 */
    fun claim(current: Long): Boolean {
        if (current == handled) return false
        handled = current
        return true
    }
}
