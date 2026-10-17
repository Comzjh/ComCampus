package com.xmu.course.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real WorkManager requests with controlled scheduling; no session or network is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoWidgetSchedulingRefreshAuditTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val executor = SynchronousExecutor()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(executor).setTaskExecutor(executor).build(),
        )
    }

    @After
    fun teardown() {
        WorkManagerImpl.getInstance(context).workDatabase.close()
        WorkManagerImpl.setDelegate(null)
    }

    @Test
    fun addingTodoWidgetRegistersOneNetworkConstrainedHourlySync() {
        val receiver = TodoWidgetReceiver()
        receiver.onEnabled(context)
        receiver.onEnabled(context)

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("tronclass_todo_hourly_sync").get()
        assertEquals("Adding a widget must register its background data sync exactly once", 1, work.size)
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(work.single().id.toString())!!
        assertEquals(3_600_000L, spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(false, spec.input.getBoolean("force_refresh", true))
    }

    @Test
    fun widgetRefreshActionEnqueuesAForcedNetworkConstrainedSync() = runBlocking {
        // Reflection lets the regression compile before the omitted published callback is restored.
        val callback = Class.forName("com.xmu.course.ui.widget.RefreshTodoWidgetAction")
            .getDeclaredConstructor().newInstance() as ActionCallback
        callback.onAction(context, object : GlanceId {}, actionParametersOf())

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("tronclass_todo_widget_manual_sync").get()
        assertEquals("The refresh button must enqueue a real data refresh", 1, work.size)
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(work.single().id.toString())!!
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(true, spec.input.getBoolean("force_refresh", false))
    }
}
