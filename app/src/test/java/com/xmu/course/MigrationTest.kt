package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.AcademicRecordEntity
import com.xmu.course.data.tronclass.model.TronCourseEntity
import com.xmu.course.data.todo.model.TodoSource
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

    /** 在 v1 schema 上手动执行既有迁移，构造一个可验证 9 -> 10 的旧数据库。 */
    private fun createV9Schema() {
        createV1Schema()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)
        AppDatabase.MIGRATION_3_4.migrate(db)
        AppDatabase.MIGRATION_4_5.migrate(db)
        AppDatabase.MIGRATION_5_6.migrate(db)
        AppDatabase.MIGRATION_6_7.migrate(db)
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        db.version = 9
        db.close()
    }

    /** 在已有 v9 数据库上执行既有迁移，构造一个可验证 13 -> 14 的旧数据库。 */
    private fun createV13Schema() {
        createV9Schema()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_9_10.migrate(db)
        AppDatabase.MIGRATION_10_11.migrate(db)
        AppDatabase.MIGRATION_11_12.migrate(db)
        AppDatabase.MIGRATION_12_13.migrate(db)
        db.version = 13
        helper.close()
    }

    @Test fun `迁移后为已有学期生成课表与默认配置`() = runTest {
        createV1Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
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
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
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

    @Test fun `v9数据库连续迁移到v12保留已有表并可插入TronCourse`() = runTest {
        createV9Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(14, db.openHelper.readableDatabase.version)
            val existingTables = db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
            ).use { cursor ->
                buildSet {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertTrue(existingTables.containsAll(setOf("semesters", "courses", "timetables", "timetable_configs", "skipped_courses")))
            assertTrue(existingTables.contains("tron_courses"))

            val course = TronCourseEntity(
                tronCourseId = 10384L,
                name = "Python程序设计",
                semester = "2026-2027秋季",
                instructor = "测试教师",
                updatedTime = 1_000L,
            )
            db.tronCourseDao().insertAll(listOf(course))
            assertEquals(listOf(course.copy(id = 1L)), db.tronCourseDao().getAll())
        } finally {
            db.close()
        }
    }

    @Test fun `v10数据库迁移到v12保留旧数据并创建Todo表`() = runTest {
        createV9Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(14, db.openHelper.readableDatabase.version)
            assertEquals(2, db.courseDao().countBySemester(1L))

            val tableExists = db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'todo_items'",
            ).use { it.moveToFirst() }
            assertTrue(tableExists)

            val todo = com.xmu.course.data.todo.model.TodoEntity(
                title = "测试待办",
                description = "迁移后可写入",
                courseId = null,
                deadline = null,
                createdTime = 1_000L,
                updatedTime = 1_000L,
            )
            db.todoDao().insert(todo)
            assertEquals("测试待办", db.todoDao().getAll().single().title)
        } finally {
            db.close()
        }
    }

    @Test fun `v11待办迁移到v12保留本地待办和完成状态`() = runTest {
        createV9Schema()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val v11 = helper.writableDatabase
        AppDatabase.MIGRATION_9_10.migrate(v11)
        AppDatabase.MIGRATION_10_11.migrate(v11)
        v11.execSQL(
            "INSERT INTO todo_items (title, description, courseId, source, deadline, completed, createdTime, updatedTime) " +
                "VALUES ('已完成手动待办', '迁移应保留', NULL, 'LOCAL', NULL, 1, 1000, 2000)",
        )
        val todoId = v11.query("SELECT last_insert_rowid()", emptyArray()).use {
            it.moveToFirst()
            it.getLong(0)
        }
        v11.version = 11
        helper.close()

        val v12 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        try {
            val migrated = v12.todoDao().getById(todoId)
            assertNotNull(migrated)
            assertTrue(migrated!!.completed)
            assertEquals(null, migrated.externalId)
        } finally {
            v12.close()
        }
    }

    @Test fun `v12迁移到v13保留两类待办并创建空baseline`() = runTest {
        createV9Schema()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val v12 = helper.writableDatabase
        AppDatabase.MIGRATION_9_10.migrate(v12)
        AppDatabase.MIGRATION_10_11.migrate(v12)
        AppDatabase.MIGRATION_11_12.migrate(v12)
        v12.execSQL(
            "INSERT INTO todo_items (title, description, courseId, source, deadline, completed, createdTime, updatedTime, externalId) " +
                "VALUES ('手动历史待办', '', NULL, 'LOCAL', 1000, 0, 1000, 1000, NULL)",
        )
        v12.execSQL(
            "INSERT INTO todo_items (title, description, courseId, source, deadline, completed, createdTime, updatedTime, externalId) " +
                "VALUES ('畅课缓存待办', '', 9001, 'TRONCLASS', 2000, 1, 1000, 1000, 'remote-1')",
        )
        v12.version = 12
        helper.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(14, db.openHelper.readableDatabase.version)
            val todos = db.todoDao().getAll()
            assertEquals(2, todos.size)
            assertTrue(todos.any { it.source == TodoSource.LOCAL.name && it.title == "手动历史待办" })
            assertTrue(todos.any { it.source == TodoSource.TRONCLASS.name && it.externalId == "remote-1" && it.completed })
            assertTrue(
                db.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'tron_todo_sync_metadata'",
                ).use { it.moveToFirst() },
            )
            assertEquals(null, db.tronTodoSyncMetadataDao().get())
        } finally {
            db.close()
        }
    }

    @Test fun `v13迁移到v14新增独立学业记录表`() = runTest {
        createV13Schema()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(14, db.openHelper.readableDatabase.version)
            assertTrue(
                db.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'academic_records'",
                ).use { it.moveToFirst() },
            )

            db.academicRecordDao().insert(
                AcademicRecordEntity(
                    name = "迁移后的学业课程",
                    creditsText = "3",
                    source = "JW_REPORT",
                ),
            )
            assertEquals("迁移后的学业课程", db.academicRecordDao().getAll().single().name)
        } finally {
            db.close()
        }
    }
}
