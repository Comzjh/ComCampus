package com.xmu.course

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.Course
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.background.BackgroundContent
import com.xmu.course.ui.background.BackgroundPipelineDefaults
import com.xmu.course.ui.background.xmuBackgrounds
import com.xmu.course.ui.settings.TimetableSettingsScreen
import com.xmu.course.ui.timetable.TimetableLayoutConfig
import com.xmu.course.ui.timetable.CourseCard
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import com.xmu.course.ui.timetable.courseCardColor
import com.xmu.course.ui.timetable.timeAxisTypography
import com.xmu.course.ui.timetable.timetableHeaderHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V053ExperienceTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun courseCardHasNoTimeTest() {
        val course = Course(
            name = "大学物理实验(08)",
            teacher = "张老师",
            location = "海韵教学楼104",
            dayOfWeek = 1,
            startSection = 1,
            duration = 4,
            weeks = (1..16).toSet(),
        )
        rule.setContent {
            Box(Modifier.size(160.dp, 224.dp)) {
                CourseCard(course = course, showTeacher = true, showLocation = true, showNote = false, onClick = {})
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("08:00-11:50", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("@海韵教学楼104", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("张老师", useUnmergedTree = true).assertExists()
    }

    @Test
    fun fullLocationDisplayTest() {
        val course = Course(
            name = "基础化学实验（二）(04)",
            teacher = "陈老师",
            location = "庄汉水楼410",
            dayOfWeek = 4,
            startSection = 5,
            duration = 4,
            weeks = (1..16).toSet(),
        )
        rule.setContent {
            Box(Modifier.size(160.dp, 224.dp)) {
                CourseCard(course = course, showTeacher = false, showNote = false, onClick = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("@庄汉水楼410", useUnmergedTree = true).assertExists()
    }

    @Test
    fun timeAxisGridAlignmentTest() {
        val config = TimetableLayoutConfig(sectionHeightDp = 64f, sectionCount = 11)
        listOf(1, 2, 5, 10).forEach { section ->
            assertEquals(config.gridLineYDp(section - 1), config.sectionTopDp(section), 0.001f)
            assertEquals(config.gridLineYDp(section), config.sectionBottomDp(section), 0.001f)
            assertEquals(config.sectionCenterDp(section), config.gridLineYDp(section - 1) + config.sectionHeightDp / 2f, 0.001f)
        }
    }

    @Test
    fun timeAxisCompactFitsCellTest() {
        val standard = timeAxisTypography(62f)
        val compact = timeAxisTypography(50f)
        val low = timeAxisTypography(44f)
        assertTrue(compact.numberSizeSp < standard.numberSizeSp)
        assertTrue(compact.timeSizeSp < standard.timeSizeSp)
        assertTrue(compact.spacingDp == 0)
        assertTrue(compact.numberLineHeightSp + 2 * compact.timeLineHeightSp <= 50)
        assertTrue(low.numberLineHeightSp + 2 * low.timeLineHeightSp <= 44)
    }

    @Test
    fun compactHeaderHeightTest() {
        val compact = timetableHeaderHeightDp(compact = true, statusBarPaddingDp = 0f)
        val standard = timetableHeaderHeightDp(compact = false, statusBarPaddingDp = 0f)
        assertTrue(compact <= 70f)
        assertTrue(compact < standard)
    }

    @Test
    fun settingsScrollTest() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val context: Context = application
        com.xmu.course.data.local.AppDatabase.closeAndResetForTest()
        val db = com.xmu.course.data.local.AppDatabase.getInstance(context)
        val timetable = insertTimetableForTest(db.openHelper.writableDatabase, "滚动测试课表")
        TimetablePrefs.load(context)
        TimetablePrefs.setCurrent(context, timetable)
        rule.setContent { MaterialTheme { TimetableSettingsScreen(onBack = {}) } }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipeUp() }
        rule.onNodeWithText("紧凑顶部栏", useUnmergedTree = true).assertExists()
    }

    @Test
    fun backgroundNavigationTest() {
        val config = TimetableConfig(
            timetableId = 1L,
            backgroundType = BackgroundType.BUILT_IN,
            backgroundValue = "jiageng",
        )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    BackgroundContent(config)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun backgroundResourceLoadTest() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(5, xmuBackgrounds.size)
        xmuBackgrounds.forEach { (value, resId) ->
            assertTrue("背景资源无效: $value", resId != 0)
            val bitmap = context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it) }
            assertNotNull("背景解码失败: $value", bitmap)
            assertTrue("背景尺寸异常: $value", bitmap.width > 0 && bitmap.height > 0)
        }
    }

    @Test
    fun backgroundBlurTest() {
        assertTrue(BackgroundPipelineDefaults.validate(0, 0f))
        assertTrue(BackgroundPipelineDefaults.validate(25, 0.70f))
        assertEquals(14, BackgroundPipelineDefaults.BUILT_IN_BLUR_RADIUS_DP)
        assertTrue(
            BackgroundPipelineDefaults.validate(
                BackgroundPipelineDefaults.BUILT_IN_BLUR_RADIUS_DP,
                BackgroundPipelineDefaults.BUILT_IN_OVERLAY_ALPHA,
            ),
        )
    }

    private var testIdSeed = 9000L

    private fun insertTimetableForTest(database: SupportSQLiteDatabase, name: String): Long {
        testIdSeed += 1
        val semesterId = testIdSeed * 10
        val timetableId = testIdSeed * 10 + 1
        database.execSQL(
            "INSERT OR REPLACE INTO semesters (id, code, name, startDate, endDate) VALUES (?, ?, ?, NULL, NULL)",
            arrayOf(semesterId, "custom-v053-$semesterId", name),
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
                "(?, 1, 1, 0, 56, 8, 11, 1, 1, 1, 0, 1.0, 'NONE', NULL, 18, -16777216, 0.35, 1.0, 0.0, 0.0, " +
                "'CENTER', 'CENTER', 1, 1)",
            arrayOf(timetableId),
        )
        return timetableId
    }
}
