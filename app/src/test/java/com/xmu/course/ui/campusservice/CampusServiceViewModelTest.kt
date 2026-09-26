package com.xmu.course.ui.campusservice

import com.xmu.course.contracts.campusservice.CampusServiceDescriptor
import com.xmu.course.contracts.campusservice.CampusServiceProvider
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.SyncPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CampusServiceViewModelTest {
    private class FakeProvider : CampusServiceProvider {
        val opened = mutableListOf<String>()
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
            CampusServiceDescriptor(
                serviceId = "xmu.jw.certificate",
                capability = ProviderCapability.CAMPUS_SERVICE,
                displayKey = "campus_service.jw.certificate",
            ),
        )

        override fun openService(serviceId: String) {
            when (serviceId) {
                "xmu.jw.academic_completion", "xmu.jw.certificate" -> opened.add(serviceId)
                else -> throw IllegalArgumentException("unsupported campus service: $serviceId")
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesProviderServiceList() {
        val viewModel = CampusServiceViewModel(FakeProvider())

        assertEquals(
            listOf("xmu.jw.academic_completion", "xmu.jw.certificate"),
            viewModel.services.map { it.serviceId },
        )
    }

    @Test
    fun clickDelegatesToProvider() {
        val provider = FakeProvider()
        val viewModel = CampusServiceViewModel(provider)

        viewModel.openService("xmu.jw.academic_completion")
        viewModel.openService("xmu.jw.certificate")

        assertEquals(
            listOf("xmu.jw.academic_completion", "xmu.jw.certificate"),
            provider.opened,
        )
        assertNull(viewModel.unsupportedServiceId.value)
    }

    @Test
    fun unsupportedServiceBecomesErrorState() = kotlinx.coroutines.runBlocking {
        val viewModel = CampusServiceViewModel(FakeProvider())

        viewModel.openService("xmu.jw.transcript")

        assertEquals("xmu.jw.transcript", viewModel.unsupportedServiceId.first())
        viewModel.consumeUnsupportedService()
        assertNull(viewModel.unsupportedServiceId.first())
    }
}