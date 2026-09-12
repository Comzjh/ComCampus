package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1 -> v2 迁移测试：手工构建 v1 schema 并插入种子数据，
 * 用 Room 以 v2 打开自动执行 MIGRATION_1_2，验证 seed 与数据保留。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    /** v1 schema（与 Room v1 生成 DDL 一致）。 */
    private fun createV1Schema() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `semesters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`code` TEXT NOT NULL, `name` TEXT NOT NULL, `startDate` TEXT, `endDate` TEXT)",
                        )
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_semesters_code` ON `semesters` (`code`)")
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `courses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`semesterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, " +
                                "`location` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startSection` INTEGER NOT NULL, " +
                                "`duration` INTEGER NOT NULL, `weeks` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                                "`color` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                                "FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE)",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_semesterId` ON `courses` (`semesterId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_dayOfWeek` ON `courses` (`dayOfWeek`)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        // 种子：1 个学期 + 2 门课程
        db.execSQL(
            "INSERT INTO semesters (code, name, startDate, endDate) VALUES ('20261', '2026-2027学年 秋季学期', '2026-09-14', NULL)",
        )
        db.execSQL(
            "INSERT INTO courses (semesterId, name, teacher, location, dayOfWeek, startSection, duration, weeks, source, color, note) " +
                "SELECT id, '高等数学', '张三', '海韵教学楼104', 1, 1, 2, '1,2,3', 'IMPORT', '#FAAC8F', '' FROM semesters",
        )
        db.execSQL(
            "INSERT INTO courses (semesterId, name, teacher, location, dayOfWeek, startSection, duration, weeks, source, color, note) " +
                "SELECT id, '大学英语', '李四', '海韵教学楼201', 3, 3, 2, '1-16', 'IMPORT', '#93D36E', 'note1' FROM semesters",
        )
        db.close()
    }

    @Test fun `迁移后为已有学期生成课表与默认配置`() = runTest {
        createV1Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        try {
            val withCount = db.timetableDao().observeAllWithCount().first()
            assertEquals(1, withCount.size)
            assertEquals("2026-2027学年 秋季学期", withCount[0].timetable.name)
            assertEquals(2, withCount[0].courseCount)
            assertEquals("2026-09-14", withCount[0].timetable.startDate)

            val config = db.timetableDao().getConfig(withCount[0].timetable.id)
            assertNotNull(config)
            assertEquals(false, config!!.showSaturday)
            assertEquals(50, config.courseHeight)
            assertEquals(1f, config.courseAlpha)
            assertEquals(BackgroundType.BUILT_IN.name, config.backgroundType)
            assertEquals(14, config.blurRadius)
            assertEquals(0.35f, config.overlayAlpha)
            assertEquals(TextHorizontalAlignment.CENTER.name, config.textHorizontalAlignment)
            assertEquals(TextVerticalAlignment.CENTER.name, config.textVerticalAlignment)
            assertEquals(true, config.showFullTimeAxis)
            assertEquals(true, config.headerCompactMode)
            assertEquals(BackgroundType.BUILT_IN.name, config.backgroundType)
            assertEquals("jiageng", config.backgroundValue)
            assertTrue(db.skippedCourseDao().getSkippedCourseIds().isEmpty())

            // 课程数据无损
            assertEquals(2, db.courseDao().countBySemester(withCount[0].timetable.semesterId))
        } finally {
            db.close()
        }
    }

    @Test fun `oldDatabaseOpenSettingsTest_v4旧库可迁移并打开v5设置默认值`() = runTest {
        createV1Schema()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
            )
            .allowMainThreadQueries()
            .build()
        try {
            val timetableId = db.timetableDao().getAll().first().id
            val config = db.timetableDao().getConfig(timetableId)
            assertNotNull(config)
            config!!
            assertEquals(true, config.showFullTimeAxis)
            assertEquals(true, config.headerCompactMode)
            assertEquals(true, config.showTime)
            assertEquals(false, config.showNote)
            assertEquals(false, config.showSaturday)
            assertEquals(false, config.showSunday)
            assertEquals(12, config.textSize)
            assertEquals(50, config.courseHeight)
            assertEquals(10, config.cornerRadius)
            assertEquals(BackgroundType.BUILT_IN.name, config.backgroundType)
            assertEquals("jiageng", config.backgroundValue)
        } finally {
            db.close()
        }
    }
}
