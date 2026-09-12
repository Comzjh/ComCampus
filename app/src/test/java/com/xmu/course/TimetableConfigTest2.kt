package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 课表显示配置测试：默认值符合 Phase 5 规范 + 每课表配置读写。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableConfigTest2 {

    private lateinit var db: AppDatabase
    private lateinit var repo: TimetableRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TimetableRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test fun `新建课表默认配置符合规范`() = runTest {
        val t = repo.createTimetable("A")
        val config = repo.getConfig(t.id)

        // 课程信息显示：教师/地点/备注 默认开，时间默认关
        assertTrue(config.showTeacher)
        assertTrue(config.showLocation)
        assertFalse(config.showNote)
        assertTrue(config.showTime)
        assertTrue(config.showFullTimeAxis)
        assertTrue(config.headerCompactMode)

        // v0.5.4 默认：隐藏周六/周日，课程名 9sp，格子 62dp
        assertFalse(config.showSaturday)
        assertFalse(config.showSunday)
        assertFalse(config.showNonCurrentWeek)
        assertEquals(12, config.textSize)
        assertEquals(50, config.courseHeight)
        assertEquals(10, config.cornerRadius)
    }

    @Test fun `两个课表配置互相独立`() = runTest {
        val t1 = repo.createTimetable("A")
        val t2 = repo.createTimetable("B")

        repo.updateConfig(repo.getConfig(t1.id).copy(showTeacher = false, showSunday = false))

        val c1 = repo.observeConfig(t1.id).first()
        val c2 = repo.observeConfig(t2.id).first()
        assertEquals(false, c1.showTeacher)
        assertEquals(false, c1.showSunday)
        assertEquals(true, c2.showTeacher)
        assertEquals(false, c2.showSunday)
    }

    @Test fun `updateConfig后observeConfig发出新值`() = runTest {
        val t = repo.createTimetable("A")
        repo.updateConfig(repo.getConfig(t.id).copy(showTime = true, showNonCurrentWeek = true))
        val config = repo.observeConfig(t.id).first()
        assertTrue(config.showTime)
        assertTrue(config.showNonCurrentWeek)
    }
}
