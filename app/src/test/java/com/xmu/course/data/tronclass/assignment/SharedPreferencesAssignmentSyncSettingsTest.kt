package com.xmu.course.data.tronclass.assignment

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TRON-08：作业自动导入的默认值只对「从未设置过」生效，
 * 用户显式选择永远优先（含已关闭的老用户）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesAssignmentSyncSettingsTest {

    private fun settings(): AssignmentSyncSettings =
        SharedPreferencesAssignmentSyncSettings(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `从未设置过时默认开启`() {
        assertTrue(settings().isAutoImportEnabled())
    }

    @Test
    fun `显式开启后保持开启`() {
        settings().setAutoImportEnabled(true)
        assertTrue(settings().isAutoImportEnabled())
    }

    @Test
    fun `显式关闭后保持关闭不被默认值覆盖`() {
        val first = settings()
        first.setAutoImportEnabled(false)
        assertFalse(
            SharedPreferencesAssignmentSyncSettings(
                ApplicationProvider.getApplicationContext<Context>(),
            ).isAutoImportEnabled(),
        )
    }
}
