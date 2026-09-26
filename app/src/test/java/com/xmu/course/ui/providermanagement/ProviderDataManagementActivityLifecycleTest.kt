package com.xmu.course.ui.providermanagement

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.xmu.course.contracts.provider.PrivacyDataOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDataManagementActivityLifecycleTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirmedDeleteAndFailureResultSurviveActivityRecreation() {
        val deleteStarted = CompletableDeferred<Unit>()
        val allowDeleteToFail = CompletableDeferred<Unit>()
        var clearCalls = 0
        val owner = object : PrivacyDataOwner {
            override suspend fun countLocalData(): Int = 23

            override suspend fun clearLocalData() {
                clearCalls++
                deleteStarted.complete(Unit)
                allowDeleteToFail.await()
                throw IllegalStateException("synthetic delete failure")
            }
        }
        val factory = ProviderDataManagementViewModelFactory("xmu.wisedu", owner)
        val key = "provider-data-management:xmu.wisedu"
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()

        try {
            val original = ViewModelProvider(controller.get(), factory)
                .get(key, ProviderDataManagementViewModel::class.java)
            original.deleteLocalData()
            assertTrue(deleteStarted.isCompleted)
            assertTrue(original.deleting.value)

            controller.configurationChange()
            val recreated = ViewModelProvider(controller.get(), factory)
                .get(key, ProviderDataManagementViewModel::class.java)

            assertSame(original, recreated)
            assertTrue(recreated.deleting.value)
            assertEquals(1, clearCalls)

            allowDeleteToFail.complete(Unit)

            assertEquals("删除失败，请稍后重试", recreated.resultMessage.value)
            assertEquals(1, clearCalls)
        } finally {
            controller.destroy()
        }
    }
}
