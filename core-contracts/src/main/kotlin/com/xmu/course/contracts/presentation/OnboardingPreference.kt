package com.xmu.course.contracts.presentation

/** App 首次使用引导完成状态的最小 presentation capability。 */
interface OnboardingPreference {
    fun isOnboardingCompleted(): Boolean

    fun setOnboardingCompleted()
}
