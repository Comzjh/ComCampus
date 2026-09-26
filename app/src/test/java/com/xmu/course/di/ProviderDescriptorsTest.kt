package com.xmu.course.di

import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.SyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderDescriptorsTest {
    @Test
    fun wiseduDescriptorIsTimetableManualOnly() {
        val descriptor = ProviderDescriptors.WISEDU

        assertEquals("xmu.wisedu", descriptor.id)
        assertEquals(setOf(ProviderCapability.TIMETABLE), descriptor.capabilities)
        assertEquals(SyncPolicy.MANUAL_ONLY, descriptor.syncPolicy)
    }

    @Test
    fun tronClassDescriptorIsTodoForegroundAllowed() {
        val descriptor = ProviderDescriptors.TRONCLASS

        assertEquals("xmu.tronclass", descriptor.id)
        assertEquals(setOf(ProviderCapability.TODO), descriptor.capabilities)
        assertEquals(SyncPolicy.FOREGROUND_ALLOWED, descriptor.syncPolicy)
    }
}