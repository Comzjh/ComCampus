package com.xmu.course.ui.auth

import com.xmu.course.data.auth.AuthStatus
import com.xmu.course.data.auth.AuthStatusSource
import com.xmu.course.data.auth.WiseduAuthObservation
import com.xmu.course.data.auth.WiseduAuthStatusController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `two services keep independent statuses`() = runTest {
        val viewModel = AuthCenterViewModel(
            FakeWiseduSource(AuthStatus.UNKNOWN),
            StaticStatusSource(AuthStatus.AUTHENTICATED),
        )

        advanceUntilIdle()

        assertEquals(AuthStatus.UNKNOWN, viewModel.uiState.value.wisedu.status)
        assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.tronClass.status)
    }

    @Test
    fun `refresh exposes expired status without changing other service`() = runTest {
        val wisedu = FakeWiseduSource(AuthStatus.AUTHENTICATED)
        val tron = MutableStatusSource(AuthStatus.EXPIRED)
        val viewModel = AuthCenterViewModel(wisedu, tron)
        advanceUntilIdle()

        wisedu.status = AuthStatus.UNKNOWN
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(AuthStatus.UNKNOWN, viewModel.uiState.value.wisedu.status)
        assertEquals(AuthStatus.EXPIRED, viewModel.uiState.value.tronClass.status)
    }

    @Test
    fun `source failure becomes unknown rather than crashing`() = runTest {
        val viewModel = AuthCenterViewModel(
            object : WiseduAuthStatusController {
                override suspend fun check(): AuthStatus = error("test")
                override fun applyObservation(observation: WiseduAuthObservation): AuthStatus = AuthStatus.UNKNOWN
                override fun clear() = Unit
            },
            StaticStatusSource(AuthStatus.AUTH_REQUIRED),
        )

        advanceUntilIdle()

        assertEquals(AuthStatus.UNKNOWN, viewModel.uiState.value.wisedu.status)
        assertEquals(AuthStatus.AUTH_REQUIRED, viewModel.uiState.value.tronClass.status)
    }

    @Test
    fun `historical import marker cannot become authenticated`() = runTest {
        val wisedu = FakeWiseduSource(AuthStatus.UNKNOWN)
        val viewModel = AuthCenterViewModel(wisedu, StaticStatusSource(AuthStatus.AUTH_REQUIRED))

        advanceUntilIdle()
        viewModel.onWiseduObservation(WiseduAuthObservation(host = "jw.xmu.edu.cn", path = "/new/index.html"))

        assertEquals(AuthStatus.UNKNOWN, viewModel.uiState.value.wisedu.status)
    }

    @Test
    fun `verified observation updates only wisedu status`() = runTest {
        val wisedu = FakeWiseduSource(AuthStatus.UNKNOWN)
        val viewModel = AuthCenterViewModel(wisedu, StaticStatusSource(AuthStatus.AUTHENTICATED))

        advanceUntilIdle()
        viewModel.onWiseduObservation(
            WiseduAuthObservation(
                host = "jw.xmu.edu.cn",
                path = "/verified-route",
                authenticatedSignal = true,
            ),
        )

        assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.wisedu.status)
        assertEquals(AuthStatus.AUTHENTICATED, viewModel.uiState.value.tronClass.status)
    }

    private class StaticStatusSource(private val value: AuthStatus) : AuthStatusSource {
        override suspend fun check(): AuthStatus = value
    }

    private class MutableStatusSource(var status: AuthStatus) : AuthStatusSource {
        override suspend fun check(): AuthStatus = status
    }

    private class FakeWiseduSource(var status: AuthStatus) : WiseduAuthStatusController {
        override suspend fun check(): AuthStatus = status

        override fun applyObservation(observation: WiseduAuthObservation): AuthStatus {
            status = if (observation.authenticatedSignal) AuthStatus.AUTHENTICATED else AuthStatus.UNKNOWN
            return status
        }

        override fun clear() {
            status = AuthStatus.UNKNOWN
        }
    }
}
