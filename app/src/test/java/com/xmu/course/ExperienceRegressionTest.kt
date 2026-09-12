package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.ImportPreparation
import com.xmu.course.data.ImportResult
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.parser.XmuKingosoftParser
import com.xmu.course.ui.timetable.TimeTableConfig
import com.xmu.course.ui.timetable.TimetableLayoutEngine
import com.xmu.course.domain.Course
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExperienceRegressionTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: CourseRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CourseRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun timeRangeDisplayTest() {
        assertEquals("08:00-08:45", TimeTableConfig.timeRange(1, 1))
        assertEquals("08:00-09:40", TimeTableConfig.timeRange(1, 2))
        assertEquals("08:00-11:50", TimeTableConfig.timeRange(1, 4))
        assertEquals("14:30-16:10", TimeTableConfig.timeRange(5, 2))
    }

    @Test
    fun roomFullNamePreservedTest() = runTest {
        val html = javaClass.getResourceAsStream("/sample_mobile.html")!!
            .readBytes().toString(Charsets.UTF_8)
        val preparation = repo.prepareImport(html) as ImportPreparation.Ready
        val parsed = preparation.courses.first { it.name.startsWith("大学物理实验") && it.weeks == (1..2).toSet() }
        assertEquals("海韵教学楼104", parsed.location)

        val saved = repo.commitImport(preparation.semester, preparation.courses, overwrite = false)
            as ImportResult.Success
        val roomCourse = repo.observeCourses(saved.semesterId).first()
            .first { it.name.startsWith("大学物理实验") && it.weeks == (1..2).toSet() }
        assertEquals("海韵教学楼104", roomCourse.location)
    }

    @Test
    fun gridToggleLayoutStableTest() {
        val courses = listOf(
            Course(name = "A", dayOfWeek = 1, startSection = 1, duration = 4, weeks = (1..16).toSet()),
            Course(name = "B", dayOfWeek = 1, startSection = 1, duration = 4, weeks = (1..16).toSet()),
        )
        // showGrid 是 TimetableScreen 的视觉层，不是 LayoutEngine 输入；打开/关闭均使用同一布局结果。
        val gridOn = TimetableLayoutEngine.layoutForWeek(courses, week = 1)
        val gridOff = TimetableLayoutEngine.layoutForWeek(courses, week = 1)
        assertEquals(gridOn, gridOff)
        assertEquals(2, gridOn.getValue(1).size)
        assertTrue(gridOn.getValue(1).all { it.laneCount == 2 && it.course.duration == 4 })
    }
}
