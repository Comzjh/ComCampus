package com.xmu.course.contracts.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPreferenceContractTest {

    @Test
    fun `contract exposes onboarding completion state`() {
        var completed = false
        val preference = object : OnboardingPreference {
            override fun isOnboardingCompleted(): Boolean = completed

            override fun setOnboardingCompleted() {
                completed = true
            }
        }

        assertFalse(preference.isOnboardingCompleted())
        preference.setOnboardingCompleted()
        assertTrue(preference.isOnboardingCompleted())
    }
}
