package com.xmu.course.ui.campusservice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xmu.course.contracts.campusservice.CampusServiceDescriptor
import com.xmu.course.contracts.campusservice.CampusServiceProvider
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.SyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CampusServiceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeProvider : CampusServiceProvider {
        var openedServiceId: String? = null

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
            openedServiceId = serviceId
        }
    }

    @Test
    fun serviceListDisplaysAllProvidedServices() {
        val viewModel = CampusServiceViewModel(FakeProvider())

        composeRule.setContent {
            CampusServiceScreen(viewModel = viewModel, onBack = {})
        }

        composeRule.onNodeWithText("本机工具").assertDoesNotExist()
        composeRule.onNodeWithText("培养方案进度").assertDoesNotExist()
composeRule.onNodeWithText("学校服务").assertIsDisplayed()
composeRule.onNodeWithText("学业完成查询").assertIsDisplayed()
        composeRule.onNodeWithText("证明申请").assertIsDisplayed()
    }

    @Test
    fun screenShowsOnlyOfficialServicesWithoutDuplicatedLocalTools() {
        val provider = FakeProvider()
        val viewModel = CampusServiceViewModel(provider)

        composeRule.setContent {
            CampusServiceScreen(
                viewModel = viewModel,
                onBack = {},
            )
        }

        composeRule.onNodeWithText("校园服务").assertIsDisplayed()

        composeRule.onNodeWithText("培养方案进度").assertDoesNotExist()
        assertEquals(null, provider.openedServiceId)
    }

    @Test
    fun clickDelegatesToProvider() {
        val provider = FakeProvider()
        val viewModel = CampusServiceViewModel(provider)

        composeRule.setContent {
            CampusServiceScreen(viewModel = viewModel, onBack = {})
        }

composeRule.onNodeWithText("学业完成查询").performClick()

        assertEquals("xmu.jw.academic_completion", provider.openedServiceId)
    }

    @Test
    fun serviceDescriptionsAreDisplayed() {
        val viewModel = CampusServiceViewModel(FakeProvider())

        composeRule.setContent {
            CampusServiceScreen(viewModel = viewModel, onBack = {})
        }

composeRule.onNodeWithText("前往厦大教务系统查看结果").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("在线申请成绩与在读证明").performScrollTo().assertIsDisplayed()
composeRule.onNodeWithText("仅在你点击时打开学校页面，不自动访问").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyServiceListShowsEmptyState() {
        val emptyProvider = object : CampusServiceProvider {
            override fun descriptor() = FakeProvider().descriptor()
            override fun services(): List<CampusServiceDescriptor> = emptyList()
            override fun openService(serviceId: String) = Unit
        }

        composeRule.setContent {
            CampusServiceScreen(viewModel = CampusServiceViewModel(emptyProvider), onBack = {})
        }

        composeRule.onNodeWithText("暂无可用服务").assertIsDisplayed()
        composeRule.onNodeWithText("本机工具").assertDoesNotExist()
        composeRule.onNodeWithText("培养方案进度").assertDoesNotExist()
    }
}
