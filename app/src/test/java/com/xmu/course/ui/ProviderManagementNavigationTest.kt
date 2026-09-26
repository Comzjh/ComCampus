package com.xmu.course.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderManagementNavigationTest {
    @Test
    fun providerManagementRouteIsCentralized() {
        assertEquals("provider_management", AppRoutes.PROVIDER_MANAGEMENT)
    }

    @Test
    fun providerDataManagementRouteIsCentralizedWithIdParam() {
        assertEquals(
            "provider_data_management/{providerId}",
            AppRoutes.PROVIDER_DATA_MANAGEMENT,
        )
    }
}
