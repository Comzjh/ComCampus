package com.xmu.course

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.import.ImportPreparation
import com.xmu.course.data.import.ImportResult
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.ui.AppRoutes
import com.xmu.course.ui.background.BackgroundContent
import com.xmu.course.ui.settings.TimetableSettingsScreen
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.timetable.TimetableLayoutConfig
import com.xmu.course.ui.timetable.timetableHeaderHeightDp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V052ExperienceTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun timeAxisAlignmentTest() {
        val config = TimetableLayoutConfig(sectionHeightDp = 56f, sectionCount = 11)
        listOf(1, 2, 5, 10).forEach { section ->
            assertEquals(config.sectionTopDp(section), config.sectionTopDp(section))
            assertEquals(config.sectionBottomDp(section), config.gridLineYDp(section))
            assertEquals(config.sectionCenterDp(section), config.sectionTopDp(section) + 28f)
        }
        assertEquals(56f * 11, config.totalHeightDp, 0.001f)
    }

    @Test
    fun timeColumnGridAlignmentTest() {
        val config = TimetableLayoutConfig(sectionHeightDp = 64f)
        listOf(1, 2, 5, 10).forEach { section ->
            // TimeColumn 的每节顶边/底边与 Grid 的同一条节次边界完全来自同一配置。
            assertEquals(config.gridLineYDp(section - 1), config.sectionTopDp(section), 0.001f)
            assertEquals(config.gridLineYDp(section), config.sectionBottomDp(section), 0.001f)
        }
    }

    @Test
    fun headerSpacingTest() {
        val statusBar = 32f
        assertTrue(timetableHeaderHeightDp(compact = true, statusBarPaddingDp = statusBar) <
            timetableHeaderHeightDp(compact = false, statusBarPaddingDp = statusBar))
    }

    @Test
    fun fullRoomDisplayTest() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = CourseRepository(db)
            val html = javaClass.getResourceAsStream("/sample_mobile.html")!!
                .readBytes().toString(Charsets.UTF_8)
            val prep = repo.prepareImport(html) as ImportPreparation.Ready
            val parsed = prep.courses.first { it.name.startsWith("大学物理实验") && it.weeks == (1..2).toSet() }
            assertEquals("海韵教学楼104", parsed.location)

            val saved = repo.commitImport(prep.semester, prep.courses, overwrite = false)
            val semesterId = (saved as ImportResult.Success).semesterId
            val roomCourse = repo.observeCourses(semesterId).first()
                .first { it.name.startsWith("大学物理实验") && it.weeks == (1..2).toSet() }
            assertEquals("海韵教学楼104", roomCourse.location)
        } finally {
            db.close()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V052NavigationTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun backgroundNavigationTest() {
        assertEquals("background_picker", AppRoutes.BACKGROUND_PICKER)
        assertEquals("background_editor", AppRoutes.BACKGROUND_EDITOR)
        val config = TimetableConfig(
            timetableId = 1L,
            backgroundType = BackgroundType.BUILT_IN,
            backgroundValue = "jiageng",
        )
        // 回归入口：内置背景曾因 layer-list + painterResource 在选择页立即崩溃。
        rule.setContent {
            MaterialTheme { Box(Modifier.size(80.dp, 120.dp)) { BackgroundContent(config) } }
        }
        rule.waitForIdle()
    }

    @Test
    fun settingsNavigationTest() {
        assertEquals("timetable_settings", AppRoutes.TIMETABLE_SETTINGS)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val context: Context = application
        AppDatabase.closeAndResetForTest()
        val db = AppDatabase.getInstance(context)
        val timetable = insertTimetableForTest(db.openHelper.writableDatabase, "大一上")
        TimetablePrefs.load(context)
        TimetablePrefs.setCurrent(context, timetable)

        rule.setContent { MaterialTheme { TimetableSettingsScreen(onBack = {}) } }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("显示网格线", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("显示完整时间轴", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("紧凑顶部栏", useUnmergedTree = true).assertExists()
    }

    private var testIdSeed = 1000L

    private fun insertTimetableForTest(database: SupportSQLiteDatabase, name: String): Long {
        testIdSeed += 1
        val semesterId = testIdSeed * 10
        val timetableId = testIdSeed * 10 + 1
        database.execSQL(
            "INSERT OR REPLACE INTO semesters (id, code, name, startDate, endDate) VALUES (?, ?, ?, NULL, NULL)",
            arrayOf(semesterId, "custom-test-$semesterId", name),
        )
        database.execSQL(
            "INSERT OR REPLACE INTO timetables (id, name, semesterId, startDate, totalWeeks, currentWeek, createdTime, color) " +
                "VALUES (?, ?, ?, '2026-09-14', 25, 1, 0, NULL)",
            arrayOf(timetableId, name, semesterId),
        )
        database.execSQL(
            "INSERT OR REPLACE INTO timetable_configs (" +
                "timetableId, showSaturday, showSunday, showNonCurrentWeek, courseHeight, cornerRadius, textSize, " +
                "showTeacher, showLocation, showTime, showNote, courseAlpha, backgroundType, backgroundValue, " +
                "blurRadius, overlayColor, overlayAlpha, cropScale, cropOffsetX, cropOffsetY, " +
                "textHorizontalAlignment, textVerticalAlignment, showFullTimeAxis, headerCompactMode) VALUES " +
                "(?, 1, 1, 0, 56, 8, 11, 1, 1, 1, 0, 1.0, 'NONE', NULL, 12, -16777216, 0.35, 1.0, 0.0, 0.0, " +
                "'CENTER', 'CENTER', 1, 1)",
            arrayOf(timetableId),
        )
        return timetableId
    }
}
