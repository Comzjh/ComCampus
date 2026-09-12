package com.xmu.course.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库。导出 schema 目录暂未配置，破坏性变更期间 fallbackToDestructiveMigration。
 */
@Database(
    entities = [SemesterEntity::class, CourseEntity::class, TimetableEntity::class, TimetableConfigEntity::class, SkippedCourseEntity::class],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun timetableDao(): TimetableDao
    abstract fun skippedCourseDao(): SkippedCourseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xmu_course.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build().also { instance = it }
            }

        /** 仅测试使用：避免 Robolectric 中单例磁盘库/后台 Flow 跨用例泄漏。 */
        fun closeAndResetForTest() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        /**
         * v1 -> v2：新增多课表与每课表配置，并为每个已有学期生成同名课表（保留老用户数据）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timetables` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`semesterId` INTEGER NOT NULL, " +
                        "`startDate` TEXT, " +
                        "`totalWeeks` INTEGER NOT NULL, " +
                        "`currentWeek` INTEGER NOT NULL, " +
                        "`createdTime` INTEGER NOT NULL, " +
                        "`color` TEXT, " +
                        "FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_timetables_semesterId` ON `timetables` (`semesterId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timetable_configs` (" +
                        "`timetableId` INTEGER PRIMARY KEY NOT NULL, " +
                        "`showSaturday` INTEGER NOT NULL, " +
                        "`showSunday` INTEGER NOT NULL, " +
                        "`showNonCurrentWeek` INTEGER NOT NULL, " +
                        "`courseHeight` INTEGER NOT NULL, " +
                        "`cornerRadius` INTEGER NOT NULL, " +
                        "`textSize` INTEGER NOT NULL, " +
                        "`showTeacher` INTEGER NOT NULL, " +
                        "`showLocation` INTEGER NOT NULL, " +
                        "`showTime` INTEGER NOT NULL, " +
                        "`showNote` INTEGER NOT NULL, " +
                        "`courseAlpha` REAL NOT NULL, " +
                        "FOREIGN KEY(`timetableId`) REFERENCES `timetables`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_timetable_configs_timetableId` ON `timetable_configs` (`timetableId`)",
                )
                // 为每个已有学期生成同名课表（老用户无感升级）
                db.execSQL(
                    "INSERT INTO timetables (name, semesterId, startDate, totalWeeks, currentWeek, createdTime) " +
                        "SELECT name, id, startDate, 25, 1, strftime('%s','now') * 1000 FROM semesters",
                )
                // 默认外观配置
                db.execSQL(
                    "INSERT INTO timetable_configs (timetableId, showSaturday, showSunday, showNonCurrentWeek, " +
                        "courseHeight, cornerRadius, textSize, showTeacher, showLocation, showTime, showNote, courseAlpha) " +
                        "SELECT id, 1, 1, 1, 56, 8, 11, 1, 1, 1, 1, 1.0 FROM timetables",
                )
            }
        }

        /**
         * v4 -> v5：新增完整时间轴与紧凑顶部栏；按 v0.5.2 默认策略更新旧显示开关。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN showFullTimeAxis INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN headerCompactMode INTEGER NOT NULL DEFAULT 1")
                // v0.5.2 默认策略：时间轴/卡片时间开启，备注默认隐藏以优先地点与教师。
                db.execSQL("UPDATE timetable_configs SET showTime = 1, showNote = 0")
            }
        }

        /**
         * v5 -> v6：初始化 v0.5.4 推荐外观；只改显示配置，课程数据不动。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE timetable_configs SET showSaturday = 0, showSunday = 0, " +
                        "showNonCurrentWeek = 0, courseHeight = 62, cornerRadius = 8, textSize = 9, " +
                        "showTeacher = 1, showLocation = 1, showNote = 0, courseAlpha = 1.0, " +
                        "showFullTimeAxis = 1, headerCompactMode = 1, " +
                        "textHorizontalAlignment = 'CENTER', textVerticalAlignment = 'CENTER'",
                )
            }
        }

        /**
         * v6 -> v7：初始化 v0.5.5 推荐外观；课程数据不变。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE timetable_configs SET textSize = 12, courseHeight = 50, cornerRadius = 10",
                )
            }
        }

        /**
         * v7 -> v8：默认使用厦大内置主题；演武大桥主题下线，替换为嘉庚楼群。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE timetable_configs SET backgroundType = 'BUILT_IN', backgroundValue = 'jiageng', blurRadius = 14 " +
                        "WHERE backgroundType = 'NONE'",
                )
                db.execSQL(
                    "UPDATE timetable_configs SET backgroundValue = 'jiageng', blurRadius = 14 " +
                        "WHERE backgroundType = 'BUILT_IN' AND (backgroundValue IS NULL OR backgroundValue = 'yanwu_bridge')",
                )
            }
        }

        /**
         * v8 -> v9：新增本地翘课状态；旧用户默认没有任何标记。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skipped_courses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`courseId` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_skipped_courses_courseId` " +
                        "ON `skipped_courses` (`courseId`)",
                )
            }
        }

        /**
         * v3 -> v4：新增课程卡片文字对齐设置。不重建表，保留用户数据。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN textHorizontalAlignment TEXT NOT NULL DEFAULT 'CENTER'")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN textVerticalAlignment TEXT NOT NULL DEFAULT 'CENTER'")
            }
        }

        /**
         * v2 -> v3：为每课表配置新增独立背景字段。不重建表，保留用户数据。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN backgroundType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN backgroundValue TEXT")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN blurRadius INTEGER NOT NULL DEFAULT 12")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN overlayColor INTEGER NOT NULL DEFAULT -16777216")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN overlayAlpha REAL NOT NULL DEFAULT 0.35")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN cropScale REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN cropOffsetX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE timetable_configs ADD COLUMN cropOffsetY REAL NOT NULL DEFAULT 0.0")
            }
        }
    }
}
