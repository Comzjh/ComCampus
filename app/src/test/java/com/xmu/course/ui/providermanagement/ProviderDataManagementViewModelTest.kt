package com.xmu.course.ui.providermanagement

import com.xmu.course.contracts.provider.AuthState
import com.xmu.course.contracts.provider.AuthStateSource
import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.ProviderManagementEntry
import com.xmu.course.contracts.provider.SyncPolicy
import com.xmu.course.ui.providermanagement.ProviderDataManagementViewModel.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

private class RecordingDataOwner : PrivacyDataOwner {
    var countCalls: Int = 0
    var clearCalls: Int = 0

    override suspend fun countLocalData(): Int {
        countCalls++
        return 23
    }

    override suspend fun clearLocalData() {
        clearCalls++
    }
}

private class ThrowingDataOwner : PrivacyDataOwner {
    override suspend fun countLocalData(): Int = throw IllegalStateException("boom")
    override suspend fun clearLocalData() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDataManagementViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(owner: PrivacyDataOwner?) = ProviderManagementEntry(
        descriptor = ProviderDescriptor(
            "xmu.wisedu",
            setOf(ProviderCapability.TIMETABLE),
            SyncPolicy.MANUAL_ONLY,
        ),
        statusSource = object : AuthStateSource {
            override fun currentState(): AuthState = AuthState.AUTHENTICATED
        },
        dataOwner = owner,
    )

    @Test
    fun countLocalDataCalledAndClearNeverCalled() {
        val owner = RecordingDataOwner()
        val viewModel = ProviderDataManagementViewModel(entry(owner))

        assertEquals(UiState.Loaded(23), viewModel.state.value)
        assertTrue(viewModel.canClearLocalData)
        assertEquals(1, owner.countCalls)
        assertEquals(0, owner.clearCalls)
    }

    @Test
    fun unavailableWhenDataOwnerMissing() {
        val viewModel = ProviderDataManagementViewModel(entry(null))

        assertEquals(UiState.Unavailable, viewModel.state.value)
        assertFalse(viewModel.canClearLocalData)
    }

    @Test
    fun unavailableWhenCountThrowsAndClearNeverCalled() {
        val viewModel = ProviderDataManagementViewModel(entry(ThrowingDataOwner()))

        assertEquals(UiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun deleteStaysBusyUntilRefreshedCountFinishes() {
        val refreshedCount = CompletableDeferred<Int>()
        var countCalls = 0
        var clearCalls = 0
        val owner = object : PrivacyDataOwner {
            override suspend fun countLocalData(): Int {
                countCalls++
                return if (countCalls == 1) 23 else refreshedCount.await()
            }

            override suspend fun clearLocalData() {
                clearCalls++
            }
        }
        val viewModel = ProviderDataManagementViewModel(entry(owner))
        assertEquals(UiState.Loaded(23), viewModel.state.value)

        viewModel.deleteLocalData()

        assertTrue(viewModel.deleting.value)
        assertEquals(1, clearCalls)
        assertEquals(UiState.Loaded(23), viewModel.state.value)
        refreshedCount.complete(0)
        assertFalse(viewModel.deleting.value)
        assertEquals(UiState.Loaded(0), viewModel.state.value)
    }

    @Test
    fun repeatedConfirmationWhileDeleteIsSuspendedDoesNotStartAnotherClear() {
        val allowDeleteToFinish = CompletableDeferred<Unit>()
        var clearCalls = 0
        val owner = object : PrivacyDataOwner {
            override suspend fun countLocalData(): Int = 23

            override suspend fun clearLocalData() {
                clearCalls++
                allowDeleteToFinish.await()
            }
        }
        val viewModel = ProviderDataManagementViewModel(entry(owner))

        viewModel.deleteLocalData()
        viewModel.deleteLocalData()

        assertTrue(viewModel.deleting.value)
        assertEquals(1, clearCalls)
        allowDeleteToFinish.complete(Unit)
        assertFalse(viewModel.deleting.value)
        assertEquals("已删除该来源的本机数据", viewModel.resultMessage.value)
        assertEquals(1, clearCalls)
    }

    @Test
    fun cancellationDoesNotReportSuccessOrFailureAndAlwaysReleasesBusyState() {
        var clearCalls = 0
        val owner = object : PrivacyDataOwner {
            override suspend fun countLocalData(): Int = 23

            override suspend fun clearLocalData() {
                clearCalls++
                throw CancellationException("synthetic cancellation")
            }
        }
        val viewModel = ProviderDataManagementViewModel(entry(owner))

        viewModel.deleteLocalData()

        assertEquals(1, clearCalls)
        assertFalse(viewModel.deleting.value)
        assertEquals(UiState.Loaded(23), viewModel.state.value)
        assertEquals(null, viewModel.resultMessage.value)
    }

    @Test
    fun providerDescriptorKeepsOnlyThreeFields() {
        val params = ProviderDescriptor::class.primaryConstructor!!.parameters
        assertEquals(listOf("id", "capabilities", "syncPolicy"), params.map(KParameter::name))
    }
}
