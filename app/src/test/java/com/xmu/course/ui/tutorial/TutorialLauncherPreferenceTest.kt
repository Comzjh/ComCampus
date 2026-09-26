package com.xmu.course.ui.tutorial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 教程入口的默认收起与用户选择都走 SharedPreferences，不引入新存储。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialLauncherPreferenceTest {

    @Before
    fun resetPreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("tutorial_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun store(): TutorialPreferenceStore =
        SharedPreferencesTutorialPreferenceStore(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `首次安装默认收起`() {
        assertTrue(store().isLauncherCollapsed())
    }

    @Test
    fun `收起后跨实例保持`() {
        store().setLauncherCollapsed(true)
        assertTrue(store().isLauncherCollapsed())
    }

    @Test
    fun `重新展开后也持久化`() {
        val first = store()
        first.setLauncherCollapsed(true)
        first.setLauncherCollapsed(false)
        assertFalse(store().isLauncherCollapsed())
    }

    @Test
    fun `教程完成不会顺便收起入口`() {
        val preferences = store()
        preferences.markCompleted("tutorial_seen_todo_v1")
        assertTrue(preferences.isLauncherCollapsed())
        preferences.setLauncherCollapsed(false)
        preferences.markCompleted("tutorial_seen_todo_v1")
        assertFalse(preferences.isLauncherCollapsed())
    }
}
