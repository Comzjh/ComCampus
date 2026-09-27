package com.xmu.course.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptRoutingTest {

    @Test
    fun `available update waits until the settings root is open`() {
        assertFalse(shouldShowUpdateDialog(AppDestination.Home.route, promptPending = true))
        assertFalse(shouldShowUpdateDialog(AppDestination.Timetable.route, promptPending = true))
        assertFalse(shouldShowUpdateDialog(AppRoutes.PROFILE_DETAIL, promptPending = true))
        assertTrue(shouldShowUpdateDialog(AppDestination.Profile.route, promptPending = true))
    }

    @Test
    fun `settings root does not show a prompt after it was consumed`() {
        assertFalse(shouldShowUpdateDialog(AppDestination.Profile.route, promptPending = false))
    }
}
