package com.xmu.course.contracts.campusservice

import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.SyncPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CampusServiceContractTest {
    private class FakeProvider : CampusServiceProvider {
        var opened: String? = null

        override fun descriptor(): ProviderDescriptor = ProviderDescriptor(
            id = "xmu.jw",
            capabilities = setOf(ProviderCapability.CAMPUS_SERVICE),
            syncPolicy = SyncPolicy.MANUAL_ONLY,
        )

        override fun services(): List<CampusServiceDescriptor> = listOf(
            CampusServiceDescriptor(
                serviceId = "xmu.jw.academic_completion",
                capability = ProviderCapability.CAMPUS_SERVICE,
                displayKey = "campus_service.jw.academic_completion",
            ),
        )

        override fun openService(serviceId: String) {
            when (serviceId) {
                "xmu.jw.academic_completion" -> opened = serviceId
                else -> throw IllegalArgumentException("unsupported campus service: $serviceId")
            }
        }
    }

    @Test
    fun `service descriptor rejects blank identity`() {
        assertFailsWith<IllegalArgumentException> {
            CampusServiceDescriptor(" ", ProviderCapability.CAMPUS_SERVICE, "key")
        }
        assertFailsWith<IllegalArgumentException> {
            CampusServiceDescriptor("xmu.jw.academic_completion", ProviderCapability.CAMPUS_SERVICE, " ")
        }
    }

    @Test
    fun `provider exposes descriptor services and opens implemented service`() {
        val provider = FakeProvider()

        val descriptor = provider.descriptor()
        assertEquals("xmu.jw", descriptor.id)
        assertEquals(setOf(ProviderCapability.CAMPUS_SERVICE), descriptor.capabilities)
        assertEquals(SyncPolicy.MANUAL_ONLY, descriptor.syncPolicy)

        val services = provider.services()
        assertEquals(1, services.size)
        assertEquals("xmu.jw.academic_completion", services[0].serviceId)
        assertTrue(services[0].displayKey.isNotBlank())

        provider.openService("xmu.jw.academic_completion")
        assertEquals("xmu.jw.academic_completion", provider.opened)
    }

    @Test
    fun `provider rejects unknown service id`() {
        val provider = FakeProvider()

        val error = assertFailsWith<IllegalArgumentException> {
            provider.openService("xmu.jw.unknown")
        }

        assertTrue(error.message!!.contains("xmu.jw.unknown"))
    }
}