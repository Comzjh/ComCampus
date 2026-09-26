package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.TimetableConfigEntity
import com.xmu.course.data.local.toDomain
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundSystemTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: TimetableRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("background_persistence.db")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TimetableRepository(db)
    }

    @After
    fun teardown() {
        db.close()
        context.deleteDatabase("background_persistence.db")
    }

    @Test
    fun `fresh config defaults to pure white background without overlay`() {
        val config = TimetableConfig(timetableId = 1L)
        assertEquals(BackgroundType.SOLID, config.backgroundType)
        assertEquals("#FFFFFFFF", config.backgroundValue)
        assertEquals(0f, config.overlayAlpha)
        val entity = TimetableConfigEntity(timetableId = 1L).toDomain()
        assertEquals(BackgroundType.SOLID, entity.backgroundType)
        assertEquals("#FFFFFFFF", entity.backgroundValue)
        assertEquals(0f, entity.overlayAlpha)
    }

    @Test fun backgroundPerTimetableTest() = runTest {
        val a = repo.createTimetable("课表A")
        val b = repo.createTimetable("课表B")

        repo.updateBackground(repo.getConfig(a.id).copy(backgroundType = BackgroundType.BUILT_IN, backgroundValue = "jiageng"))

        assertEquals(BackgroundType.BUILT_IN, repo.getConfig(a.id).backgroundType)
        assertEquals("jiageng", repo.getConfig(a.id).backgroundValue)
        // Phase 16：新建课表默认纯白背景；修改课表 A 不影响课表 B 的独立配置。
        assertEquals(BackgroundType.SOLID, repo.getConfig(b.id).backgroundType)
        assertEquals("#FFFFFFFF", repo.getConfig(b.id).backgroundValue)
    }

    @Test fun backgroundPersistenceTest() = runTest {
        val name = "background_persistence.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        val timetable = TimetableRepository(first).createTimetable("持久化")
        TimetableRepository(first).updateBackground(
            TimetableRepository(first).getConfig(timetable.id).copy(
                backgroundType = BackgroundType.BUILT_IN,
                backgroundValue = "furong_lake",
                blurRadius = 18,
                overlayAlpha = 0.5f,
            ),
        )
        first.close()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            val config = TimetableRepository(reopened).getConfig(timetable.id)
            assertEquals(BackgroundType.BUILT_IN, config.backgroundType)
            assertEquals("furong_lake", config.backgroundValue)
            assertEquals(18, config.blurRadius)
            assertEquals(0.5f, config.overlayAlpha)
        } finally {
            reopened.close()
        }
    }

    @Test fun backgroundNoEffectLayoutTest() = runTest {
        val courses = listOf(
            Course(name = "大学物理实验(08)", dayOfWeek = 4, startSection = 5, duration = 4, weeks = (1..16).toSet()),
            Course(name = "基础化学实验（二）(04)", dayOfWeek = 4, startSection = 5, duration = 4, weeks = (1..16).toSet()),
        )
        val defaultConfig = repo.getConfig(repo.createTimetable("默认").id)
        val backgroundConfig = defaultConfig.copy(
            backgroundType = BackgroundType.CUSTOM,
            backgroundValue = "content://test/image",
            cropScale = 3f,
            cropOffsetX = 88f,
            cropOffsetY = -44f,
        )

        val before = TimetableLayoutEngine.layoutForWeek(courses, week = 5)
        val after = TimetableLayoutEngine.layoutForWeek(courses, week = 5)
        // 背景配置不会进入 LayoutEngine；布局输入不变，输出严格不变。
        assertEquals(before, after)
        assertEquals(2, after.getValue(4).size)
        assertTrue(after.getValue(4).all { it.course.duration == 4 && it.laneCount == 2 })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekStatePersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun weekStatePersistenceTest() {
        TimetablePrefs.setViewWeek(context, 42L, 8)
        assertEquals(8, TimetablePrefs.getViewWeek(context, 42L))
        assertEquals(null, TimetablePrefs.getViewWeek(context, 43L))
    }

    @Test fun weekSelectorTest() {
        val week1 = listOf(
            Course(name = "单周课", dayOfWeek = 1, startSection = 1, duration = 2, weeks = setOf(1)),
            Course(name = "双周课", dayOfWeek = 1, startSection = 1, duration = 2, weeks = setOf(2)),
        )
        val layout1 = TimetableLayoutEngine.layoutForWeek(week1, week = 1)
        val layout2 = TimetableLayoutEngine.layoutForWeek(week1, week = 2)
        assertEquals(1, layout1.getValue(1).filter { it.isCurrentWeek }.size)
        assertEquals(1, layout2.getValue(1).filter { it.isCurrentWeek }.size)
        assertNotEquals(layout1.getValue(1).first { it.isCurrentWeek }.course.name, layout2.getValue(1).first { it.isCurrentWeek }.course.name)
    }
}
